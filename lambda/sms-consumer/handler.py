"""
Pulse SMS consumer -- AWS Lambda handler triggered by an SQS event source
mapping on `sms-queue`. It mirrors the email consumer (lambda/email-consumer):
same SNS envelope, same Redis claim, same partial batch response. The only
real difference is the provider: Twilio's Messages API instead of SES.

Twilio is called with the standard library (urllib) rather than the twilio
SDK. Pulse needs one endpoint, and skipping the SDK keeps the package small.

Retryable vs. permanent failures: a Twilio 5xx, a 429 (rate limited) or a
network error may succeed later, so the message is reported as failed and SQS
retries it. A missing phone number or a Twilio 4xx (for example an invalid
number) will fail the same way every time, so retrying only delays the
inevitable: the job is marked FAILED right away and the message is NOT
reported as failed, which lets SQS delete it instead of retrying it 3 times.

Dry run: when SMS_DRY_RUN is "true" (the default when no Twilio credentials
are configured), the handler logs the SMS instead of sending it, so the full
pipeline still runs locally and in environments without a Twilio account.
"""

import base64
import json
import logging
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid

import psycopg2
import redis

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# ── Config (env vars set by Terraform; localhost defaults match
#    docker-compose) ──────────────────────────────────────────────────────────
DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = os.environ.get("DB_PORT", "5433")
DB_NAME = os.environ.get("DB_NAME", "pulse")
DB_USER = os.environ.get("DB_USER", "pulse")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "pulse")

REDIS_HOST = os.environ.get("REDIS_HOST", "localhost")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

TWILIO_ACCOUNT_SID = os.environ.get("TWILIO_ACCOUNT_SID", "")
TWILIO_AUTH_TOKEN = os.environ.get("TWILIO_AUTH_TOKEN", "")
TWILIO_FROM_NUMBER = os.environ.get("TWILIO_FROM_NUMBER", "")
TWILIO_API_BASE = os.environ.get("TWILIO_API_BASE", "https://api.twilio.com")
TWILIO_TIMEOUT_SECONDS = 10

SMS_DRY_RUN = os.environ.get(
    "SMS_DRY_RUN", "false" if TWILIO_ACCOUNT_SID else "true"
).lower() == "true"

# SMS bodies over 1,600 characters are rejected by Twilio.
MAX_SMS_LENGTH = 1600

IDEMPOTENCY_TTL_SECONDS = 60 * 60 * 24  # 24h de-dup window

# Must match the queue's RedrivePolicy.maxReceiveCount
# (var.sqs_max_receive_count in Terraform, setup-localstack.sh locally).
MAX_RECEIVE_COUNT = 3

_redis_client = None


class PermanentSendError(Exception):
    """A failure that will repeat on every retry (bad input, rejected number)."""


class RetryableSendError(Exception):
    """A failure that may succeed later (provider outage, rate limit, network)."""


def get_redis():
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
    return _redis_client


def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME, user=DB_USER, password=DB_PASSWORD
    )


def parse_notification(sqs_record_body: str) -> dict:
    """Unwrap the SNS envelope in an SQS record body and return the
    notification payload published by NotificationEventPublisher."""
    envelope = json.loads(sqs_record_body)
    return json.loads(envelope["Message"])


def try_claim(job_id: str, channel: str) -> bool:
    """Atomic check-and-set: True if this delivery should send, False if
    another delivery of the same job and channel already claimed it."""
    key = f"processed:{job_id}:{channel}"
    return bool(get_redis().set(name=key, value="1", nx=True, ex=IDEMPOTENCY_TTL_SECONDS))


def release_claim(job_id: str, channel: str) -> None:
    """Undo a claim after a retryable failure so the retry can send."""
    get_redis().delete(f"processed:{job_id}:{channel}")


def build_sms_text(notification: dict) -> str:
    """Prefix the subject when there is one, since SMS has no subject line."""
    subject = (notification.get("subject") or "").strip()
    body = notification.get("body") or ""
    return f"{subject}: {body}" if subject else body


def send_sms(notification: dict) -> str:
    """Send the notification through Twilio and return the message SID."""
    to_number = (notification.get("recipientPhone") or "").strip()
    if not to_number:
        raise PermanentSendError("recipientPhone is required for the SMS channel")

    text = build_sms_text(notification)
    if not text:
        raise PermanentSendError("SMS body is empty")
    if len(text) > MAX_SMS_LENGTH:
        raise PermanentSendError(f"SMS text is {len(text)} characters; the limit is {MAX_SMS_LENGTH}")

    if SMS_DRY_RUN:
        sid = f"dry-run-{uuid.uuid4()}"
        logger.info("SMS dry run, not sending: to=%s sid=%s text=%r", to_number, sid, text)
        return sid

    url = f"{TWILIO_API_BASE}/2010-04-01/Accounts/{TWILIO_ACCOUNT_SID}/Messages.json"
    data = urllib.parse.urlencode(
        {"To": to_number, "From": TWILIO_FROM_NUMBER, "Body": text}
    ).encode()
    credentials = base64.b64encode(f"{TWILIO_ACCOUNT_SID}:{TWILIO_AUTH_TOKEN}".encode()).decode()
    request = urllib.request.Request(
        url, data=data, method="POST", headers={"Authorization": f"Basic {credentials}"}
    )

    try:
        with urllib.request.urlopen(request, timeout=TWILIO_TIMEOUT_SECONDS) as response:
            return json.loads(response.read())["sid"]
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode(errors="replace")[:500]
        if exc.code == 429 or exc.code >= 500:
            raise RetryableSendError(f"Twilio {exc.code}: {detail}") from exc
        raise PermanentSendError(f"Twilio {exc.code}: {detail}") from exc
    except (urllib.error.URLError, TimeoutError) as exc:
        raise RetryableSendError(f"Could not reach Twilio: {exc}") from exc


def record_delivery_attempt(
    job_id: str,
    channel: str,
    attempt_number: int,
    success: bool,
    provider_message_id: str | None = None,
    error_message: str | None = None,
    final: bool = False,
) -> None:
    """Insert a delivery_attempts row and update the job's status.

    A failure only marks the job FAILED when it is final: either a permanent
    error, or the last attempt before SQS moves the message to the DLQ.
    """
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
                    (job_id, channel, attempt_number, provider_message_id),
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
                if final:
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

            if channel != "SMS":
                # Defensive guard: the SNS filter policy should already keep
                # non-SMS messages off this queue.
                logger.info("Skipping non-SMS message: jobId=%s channel=%s", job_id, channel)
                continue

            claimed = try_claim(job_id, channel)
            if not claimed:
                logger.info("Duplicate delivery, skipping: jobId=%s", job_id)
                continue

            sid = send_sms(notification)
            record_delivery_attempt(
                job_id, channel, attempt_number, success=True, provider_message_id=sid
            )
            logger.info("Delivered: jobId=%s twilioSid=%s", job_id, sid)

        except PermanentSendError as exc:
            # Keep the claim: retrying cannot help, and a redelivery of this
            # message should be skipped as already handled.
            logger.warning("Permanent failure, not retrying: jobId=%s error=%s", job_id, exc)
            try:
                record_delivery_attempt(
                    job_id, channel, attempt_number, success=False,
                    error_message=str(exc), final=True,
                )
            except Exception:
                logger.exception("Failed to record permanent failure for %s", message_id)
                if claimed:
                    release_claim(job_id, channel)
                batch_item_failures.append({"itemIdentifier": message_id})

        except Exception as exc:
            logger.exception("Failed to process message %s", message_id)

            if claimed:
                release_claim(job_id, channel)

            if job_id and channel:
                try:
                    record_delivery_attempt(
                        job_id, channel, attempt_number, success=False,
                        error_message=str(exc), final=attempt_number >= MAX_RECEIVE_COUNT,
                    )
                except Exception:
                    logger.exception("Also failed to record failure in DB for %s", message_id)

            batch_item_failures.append({"itemIdentifier": message_id})

    return {"batchItemFailures": batch_item_failures}
