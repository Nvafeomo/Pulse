"""
Local test harness for the email consumer Lambda.

Runs handler.handler() directly in-process -- no SAM CLI, no real Lambda
deploy -- against the same docker-compose services (postgres, redis,
localstack) the Spring Boot app already uses locally. This is the fast
iteration loop; Terraform + a real deploy come later.

Usage:
    docker compose up -d postgres redis localstack aws-init
    pip install -r lambda/email-consumer/requirements.txt
    python lambda/email-consumer/local_test.py
"""

import hashlib
import json
import os
import uuid

os.environ.setdefault("DB_HOST", "localhost")
os.environ.setdefault("DB_PORT", "5433")
os.environ.setdefault("REDIS_HOST", "localhost")
os.environ.setdefault("REDIS_PORT", "6379")
os.environ.setdefault("SES_ENDPOINT", "http://localhost:4566")
os.environ.setdefault("SES_SENDER", "notifications@pulse.dev")

import boto3
import psycopg2

import handler


def seed_job(conn) -> str:
    """Insert an api_key + notification_job row, mirroring what
    NotificationService.createJob() does in the Spring app, so the Lambda
    has a real row to update instead of a mock."""
    job_id = str(uuid.uuid4())
    api_key_id = str(uuid.uuid4())
    key_hash = hashlib.sha256(b"local-test-key").hexdigest()

    with conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO api_keys (id, key_hash, name) VALUES (%s, %s, %s)",
            (api_key_id, key_hash, "local-test"),
        )
        cur.execute(
            """
            INSERT INTO notification_jobs
                (id, api_key_id, idempotency_key, channels, status,
                 recipient_email, subject, body)
            VALUES (%s, %s, %s, 'EMAIL', 'PROCESSING', %s, %s, %s)
            """,
            (
                job_id,
                api_key_id,
                job_id,
                "test@example.com",
                "Local Lambda test",
                "If you can read this, the consumer works.",
            ),
        )
    return job_id


def sqs_event_for(job_id: str, receive_count: int = 1) -> dict:
    """Fake SQS-triggers-Lambda event, with the message wrapped in an SNS
    envelope -- matching what SQS actually delivers when subscribed to an
    SNS topic without RawMessageDelivery enabled."""
    notification = {
        "jobId": job_id,
        "channel": "EMAIL",
        "recipientEmail": "test@example.com",
        "recipientPhone": "",
        "subject": "Local Lambda test",
        "body": "If you can read this, the consumer works.",
    }
    sns_envelope = {
        "Type": "Notification",
        "MessageId": str(uuid.uuid4()),
        "TopicArn": "arn:aws:sns:us-east-1:000000000000:pulse-notifications",
        "Message": json.dumps(notification),
        "MessageAttributes": {"channel": {"Type": "String", "Value": "EMAIL"}},
    }
    return {
        "Records": [
            {
                "messageId": str(uuid.uuid4()),
                "body": json.dumps(sns_envelope),
                "attributes": {"ApproximateReceiveCount": str(receive_count)},
            }
        ]
    }


def ensure_ses_sender_verified():
    # LocalStack's SES mock is generally permissive, but verifying the
    # sender identity mirrors what a real send would require.
    ses = boto3.client("ses", region_name="us-east-1", endpoint_url=os.environ["SES_ENDPOINT"])
    ses.verify_email_identity(EmailAddress=os.environ["SES_SENDER"])


def main():
    conn = psycopg2.connect(
        host="localhost", port="5433", dbname="pulse", user="pulse", password="pulse"
    )

    ensure_ses_sender_verified()
    job_id = seed_job(conn)
    print(f"Seeded notification_job {job_id} with status=PROCESSING")

    print("\n--- Invocation 1 (should send) ---")
    result = handler.handler(sqs_event_for(job_id), None)
    print("batchItemFailures:", result["batchItemFailures"])

    print("\n--- Invocation 2, same jobId (should be skipped as duplicate) ---")
    result = handler.handler(sqs_event_for(job_id), None)
    print("batchItemFailures:", result["batchItemFailures"])

    with conn.cursor() as cur:
        cur.execute("SELECT status FROM notification_jobs WHERE id = %s", (job_id,))
        print(f"\nFinal job status: {cur.fetchone()[0]}")

        cur.execute(
            "SELECT status, attempt_number, provider_message_id FROM delivery_attempts WHERE job_id = %s",
            (job_id,),
        )
        rows = cur.fetchall()
        print(f"delivery_attempts rows ({len(rows)} total -- should be 1, not 2):")
        for row in rows:
            print("  ", row)

    conn.close()


if __name__ == "__main__":
    main()
