"""
Pulse email consumer -- AWS Lambda handler triggered by an SQS event source
mapping on `email-queue`.

Why Lambda instead of a long-running poller: AWS itself polls the queue and
invokes this function with a batch of messages whenever any arrive. There's
no idle process, no poller loop to keep alive, and it scales to zero between
notifications. This is the standard "FaaS" pattern for event-driven queue
consumers.

Message shape: the queue is subscribed to the `pulse-notifications` SNS
topic WITHOUT RawMessageDelivery enabled (see scripts/setup-localstack.sh),
so each SQS message body is an SNS envelope:

    {"Type": "Notification", "MessageId": "...", "TopicArn": "...",
     "Message": "{\"jobId\": \"...\", \"channel\": \"EMAIL\", ...}",
     "MessageAttributes": {...}, ...}

The actual notification payload published by NotificationEventPublisher.java
is the JSON-encoded string inside "Message" -- it has to be unwrapped and
parsed a second time.

Idempotency: Redis SET NX is used as an atomic "claim" on (jobId, channel)
before sending. If the send fails partway through, the claim is released so
a retried delivery can try again -- a naive claim-and-never-release would
silently drop any message that failed on its first attempt, since every
retry would see it as "already processed" and skip it.

Retries / DLQ: this function does NOT implement retry logic itself. The SQS
queue's RedrivePolicy (maxReceiveCount=3) handles that at the queue level --
consistent with the rest of Pulse's MVP scope. We report failed messages
individually via `batchItemFailures` (SQS's "partial batch response"
feature) so one bad message doesn't force the whole batch to be redelivered.
"""

import json
import logging
import os

import boto3
import psycopg2
import redis

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# ── Config (env vars set in the Lambda console/Terraform; sensible
#    localhost defaults so this also runs against docker-compose) ──────────
DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5433")
DB_NAME = os.environ.get("DB_NAME", "pulse")
DB_USER = os.environ.get("DB_USER", "pulse")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "pulse")

REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

AWS_REGION = os.environ.get("AWS_REGION", "us-east-1")
SES_ENDPOINT = os.environ.get("SES_ENDPOINT")  # set for LocalStack; unset in real AWS
SES_SENDER = os.environ.get("SES_SENDER", "notifications@pulse.dev")

IDEMPOTENCY_TTL_SECONDS = 60 * 60 * 24  # 24h de-dup window

# Must match the queue's RedrivePolicy.maxReceiveCount
# (scripts/setup-localstack.sh today; a Terraform var later).
MAX_RECEIVE_COUNT = 3

_redis_client = None
_ses_client = None


def get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
    return _redis_client


def get_ses():
    global _ses_client
    if _ses_client is None:
        kwargs = {"region_name": AWS_REGION}
        if SES_ENDPOINT:
            kwargs["endpoint_url"] = SES_ENDPOINT
        _ses_client = boto3.client("ses", **kwargs)
    return _ses_client


def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )


def parse_notification(sqs_record_body: str) -> dict:
    envelope = json.loads(sqs_record_body)
    return json.loads(envelope["Message"])


def try_claim(job_id: str, channel: str) -> bool:
    """Atomic check-and-set. Returns True if this invocation won the claim
    (i.e. is the one that should actually send), False if another
    delivery already claimed it."""
    key = f"processed:{job_id}:{channel}"
    return bool(get_redis().set(name=key, value="1", nx=True, ex=IDEMPOTENCY_TTL_SECONDS))


def release_claim(job_id: str, channel: str) -> None:
    """Undo a claim after a failed send so a retry can attempt again."""
    get_redis().delete(f"processed:{job_id}:{channel}")


def send_email(notification: dict) -> str:
    response = get_ses().send_email(
        Source=SES_SENDER,
        Destination={"ToAddresses": [notification["recipientEmail"]]},
        Message={
            "Subject": {"Data": notification.get("subject") or "(no subject)"},
            "Body": {"Text": {"Data": notification["body"]}},
        },
    )
    return response["MessageId"]


def record_delivery_attempt(
    job_id: str,
    channel: str,
    attempt_number: int,
    success: bool,
    ses_message_id: str | None = None,
    error_message: str | None = None,
) -> None:
    conn = get_db_connection()
    try:
        with conn, conn.cursor() as cur:
            if success:
                cur.execute(
                    """
                    INSERT INTO delivery_attempts
                        (job_id, channel, status, attempt_number, delivered_at, provider_message_id)
                    VALUES (%s, %s, 'DELIVERED', %s, now(), %s)
                    """,
                    (job_id, channel, attempt_number, ses_message_id),
                )
                cur.execute(
                    "UPDATE notification_jobs SET status = 'DELIVERED', updated_at = now() WHERE id = %s",
                    (job_id,),
                )
            else:
                cur.execute(
                    """
                    INSERT INTO delivery_attempts
                        (job_id, channel, status, attempt_number, failed_at, error_message)
                    VALUES (%s, %s, 'FAILED', %s, now(), %s)
                    """,
                    (job_id, channel, attempt_number, (error_message or "")[:1000]),
                )
                # Only flip the job to a terminal FAILED state once SQS is
                # about to give up on it (next receive sends it to the DLQ).
                # Otherwise a message that fails once but succeeds on retry
                # would have been incorrectly marked FAILED already.
                if attempt_number >= MAX_RECEIVE_COUNT:
                    cur.execute(
                        "UPDATE notification_jobs SET status = 'FAILED', updated_at = now() WHERE id = %s",
                        (job_id,),
                    )
    finally:
        conn.close()


def handler(event, context):
    batch_item_failures = []

    for record in event.get("Records", []):
        message_id = record["messageId"]
        attempt_number = int(record.get("attributes", {}).get("ApproximateReceiveCount", "1"))
        job_id = None
        channel = None
        claimed = False

        try:
            notification = parse_notification(record["body"])
            job_id = notification["jobId"]
            channel = notification["channel"]

            if channel != "EMAIL":
                # Defensive guard -- the SNS subscription filter policy
                # should already keep non-EMAIL messages off this queue.
                logger.info("Skipping non-email message: jobId=%s channel=%s", job_id, channel)
                continue

            claimed = try_claim(job_id, channel)
            if not claimed:
                logger.info("Duplicate delivery, skipping: jobId=%s", job_id)
                continue

            ses_message_id = send_email(notification)
            record_delivery_attempt(
                job_id, channel, attempt_number, success=True, ses_message_id=ses_message_id
            )
            logger.info("Delivered: jobId=%s sesMessageId=%s", job_id, ses_message_id)

        except Exception as exc:
            logger.exception("Failed to process message %s", message_id)

            if claimed:
                release_claim(job_id, channel)

            if job_id and channel:
                try:
                    record_delivery_attempt(
                        job_id, channel, attempt_number, success=False, error_message=str(exc)
                    )
                except Exception:
                    logger.exception("Also failed to record failure in DB for %s", message_id)

            batch_item_failures.append({"itemIdentifier": message_id})

    return {"batchItemFailures": batch_item_failures}
