"""
Runs the email consumer continuously against docker-compose, standing in for
the SQS -> Lambda trigger that AWS provides in production.

Each worker long-polls `email-queue` on LocalStack, hands the batch to
handler.handler() in the same event shape Lambda uses, then deletes the
messages that succeeded. Failed messages are left on the queue, so SQS
redelivers them and eventually moves them to the DLQ, just like in AWS.

Usage:
    docker compose up -d
    pip install -r lambda/email-consumer/requirements.txt
    python lambda/email-consumer/local_poller.py --workers 4
"""

import argparse
import logging
import os
import threading
import time

os.environ.setdefault("DB_HOST", "localhost")
os.environ.setdefault("DB_PORT", "5433")
os.environ.setdefault("REDIS_HOST", "localhost")
os.environ.setdefault("REDIS_PORT", "6379")
os.environ.setdefault("SES_ENDPOINT", "http://localhost:4566")
os.environ.setdefault("SES_SENDER", "notifications@pulse.dev")
os.environ.setdefault("AWS_ACCESS_KEY_ID", "test")
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", "test")

import boto3

import handler

ENDPOINT = os.environ["SES_ENDPOINT"]
QUEUE_URL = os.environ.get(
    "EMAIL_QUEUE_URL",
    "http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/email-queue",
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(threadName)s %(message)s")
log = logging.getLogger("local_poller")


def to_lambda_event(messages: list[dict]) -> dict:
    """Convert an SQS ReceiveMessage response into a Lambda SQS event."""
    return {
        "Records": [
            {
                "messageId": m["MessageId"],
                "receiptHandle": m["ReceiptHandle"],
                "body": m["Body"],
                "attributes": m.get("Attributes", {}),
            }
            for m in messages
        ]
    }


def worker(stop: threading.Event, stats: dict, lock: threading.Lock) -> None:
    sqs = boto3.client("sqs", region_name="us-east-1", endpoint_url=ENDPOINT)

    while not stop.is_set():
        response = sqs.receive_message(
            QueueUrl=QUEUE_URL,
            MaxNumberOfMessages=10,
            WaitTimeSeconds=5,
            AttributeNames=["ApproximateReceiveCount"],
        )
        messages = response.get("Messages", [])
        if not messages:
            continue

        result = handler.handler(to_lambda_event(messages), None)
        failed = {f["itemIdentifier"] for f in result["batchItemFailures"]}
        succeeded = [m for m in messages if m["MessageId"] not in failed]

        if succeeded:
            sqs.delete_message_batch(
                QueueUrl=QUEUE_URL,
                Entries=[
                    {"Id": str(i), "ReceiptHandle": m["ReceiptHandle"]}
                    for i, m in enumerate(succeeded)
                ],
            )

        with lock:
            stats["processed"] += len(succeeded)
            stats["failed"] += len(failed)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--workers", type=int, default=4, help="concurrent pollers (default 4)")
    args = parser.parse_args()

    boto3.client("ses", region_name="us-east-1", endpoint_url=ENDPOINT).verify_email_identity(
        EmailAddress=os.environ["SES_SENDER"]
    )

    stop = threading.Event()
    lock = threading.Lock()
    stats = {"processed": 0, "failed": 0}
    threads = [
        threading.Thread(target=worker, args=(stop, stats, lock), name=f"worker-{i}", daemon=True)
        for i in range(args.workers)
    ]
    for t in threads:
        t.start()

    log.info("Polling %s with %d workers. Ctrl+C to stop.", QUEUE_URL, args.workers)
    try:
        while True:
            time.sleep(10)
            with lock:
                log.info("processed=%d failed=%d", stats["processed"], stats["failed"])
    except KeyboardInterrupt:
        stop.set()
        for t in threads:
            t.join(timeout=10)


if __name__ == "__main__":
    main()
