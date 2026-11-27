"""Unit tests for the SMS consumer. Redis, Postgres and Twilio are mocked, so
these run without docker-compose:

    pip install -r lambda/sms-consumer/requirements.txt
    python -m unittest discover lambda/sms-consumer
"""

import io
import json
import unittest
import urllib.error
from unittest import mock

import handler


def sqs_event(payload: dict, receive_count: int = 1, message_id: str = "msg-1") -> dict:
    """Build a Lambda SQS event whose body is an SNS envelope, as in production."""
    envelope = {"Type": "Notification", "Message": json.dumps(payload)}
    return {
        "Records": [
            {
                "messageId": message_id,
                "body": json.dumps(envelope),
                "attributes": {"ApproximateReceiveCount": str(receive_count)},
            }
        ]
    }


def sms_payload(**overrides) -> dict:
    payload = {
        "jobId": "job-1",
        "channel": "SMS",
        "recipientEmail": "",
        "recipientPhone": "+15555550100",
        "subject": "",
        "body": "Your order shipped",
    }
    payload.update(overrides)
    return payload


def twilio_http_error(code: int) -> urllib.error.HTTPError:
    return urllib.error.HTTPError(
        "https://api.twilio.com", code, "error", {}, io.BytesIO(b'{"message": "error"}')
    )


class HandlerTest(unittest.TestCase):
    def setUp(self):
        self.claim = mock.patch.object(handler, "try_claim", return_value=True).start()
        self.release = mock.patch.object(handler, "release_claim").start()
        self.record = mock.patch.object(handler, "record_delivery_attempt").start()
        self.addCleanup(mock.patch.stopall)

    def test_delivers_and_records_success(self):
        with mock.patch.object(handler, "send_sms", return_value="SM123"):
            result = handler.handler(sqs_event(sms_payload()), None)

        self.assertEqual(result, {"batchItemFailures": []})
        self.record.assert_called_once_with(
            "job-1", "SMS", 1, success=True, provider_message_id="SM123"
        )

    def test_duplicate_delivery_is_skipped(self):
        self.claim.return_value = False
        with mock.patch.object(handler, "send_sms") as send:
            result = handler.handler(sqs_event(sms_payload()), None)

        send.assert_not_called()
        self.assertEqual(result, {"batchItemFailures": []})

    def test_non_sms_message_is_skipped(self):
        with mock.patch.object(handler, "send_sms") as send:
            result = handler.handler(sqs_event(sms_payload(channel="EMAIL")), None)

        send.assert_not_called()
        self.claim.assert_not_called()
        self.assertEqual(result, {"batchItemFailures": []})

    def test_retryable_failure_releases_claim_and_is_retried(self):
        with mock.patch.object(handler, "send_sms", side_effect=handler.RetryableSendError("503")):
            result = handler.handler(sqs_event(sms_payload()), None)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "msg-1"}]})
        self.release.assert_called_once_with("job-1", "SMS")
        self.assertFalse(self.record.call_args.kwargs["final"])

    def test_last_retryable_attempt_marks_job_failed(self):
        with mock.patch.object(handler, "send_sms", side_effect=handler.RetryableSendError("503")):
            handler.handler(sqs_event(sms_payload(), receive_count=handler.MAX_RECEIVE_COUNT), None)

        self.assertTrue(self.record.call_args.kwargs["final"])

    def test_permanent_failure_is_not_retried(self):
        result = handler.handler(sqs_event(sms_payload(recipientPhone="")), None)

        self.assertEqual(result, {"batchItemFailures": []})
        self.release.assert_not_called()
        self.assertTrue(self.record.call_args.kwargs["final"])
        self.assertFalse(self.record.call_args.kwargs["success"])

    def test_one_bad_message_does_not_fail_the_batch(self):
        good = sqs_event(sms_payload(jobId="job-good"), message_id="good")["Records"]
        bad = sqs_event(sms_payload(jobId="job-bad"), message_id="bad")["Records"]

        def send(notification):
            if notification["jobId"] == "job-bad":
                raise handler.RetryableSendError("timeout")
            return "SM1"

        with mock.patch.object(handler, "send_sms", side_effect=send):
            result = handler.handler({"Records": good + bad}, None)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "bad"}]})


class SendSmsTest(unittest.TestCase):
    def setUp(self):
        patches = {
            "SMS_DRY_RUN": False,
            "TWILIO_ACCOUNT_SID": "AC123",
            "TWILIO_AUTH_TOKEN": "token",
            "TWILIO_FROM_NUMBER": "+15555550199",
        }
        for name, value in patches.items():
            mock.patch.object(handler, name, value).start()
        self.addCleanup(mock.patch.stopall)

    def test_posts_to_twilio_and_returns_sid(self):
        response = mock.MagicMock()
        response.__enter__.return_value.read.return_value = b'{"sid": "SM999"}'

        with mock.patch("urllib.request.urlopen", return_value=response) as urlopen:
            sid = handler.send_sms(sms_payload(subject="Order"))

        self.assertEqual(sid, "SM999")
        request = urlopen.call_args.args[0]
        self.assertIn("/Accounts/AC123/Messages.json", request.full_url)
        self.assertIn(b"Body=Order%3A+Your+order+shipped", request.data)

    def test_twilio_server_error_is_retryable(self):
        with mock.patch("urllib.request.urlopen", side_effect=twilio_http_error(503)):
            with self.assertRaises(handler.RetryableSendError):
                handler.send_sms(sms_payload())

    def test_twilio_rate_limit_is_retryable(self):
        with mock.patch("urllib.request.urlopen", side_effect=twilio_http_error(429)):
            with self.assertRaises(handler.RetryableSendError):
                handler.send_sms(sms_payload())

    def test_twilio_bad_request_is_permanent(self):
        with mock.patch("urllib.request.urlopen", side_effect=twilio_http_error(400)):
            with self.assertRaises(handler.PermanentSendError):
                handler.send_sms(sms_payload())

    def test_network_error_is_retryable(self):
        with mock.patch("urllib.request.urlopen", side_effect=urllib.error.URLError("down")):
            with self.assertRaises(handler.RetryableSendError):
                handler.send_sms(sms_payload())

    def test_too_long_message_is_permanent(self):
        with self.assertRaises(handler.PermanentSendError):
            handler.send_sms(sms_payload(body="x" * (handler.MAX_SMS_LENGTH + 1)))

    def test_dry_run_does_not_call_twilio(self):
        with mock.patch.object(handler, "SMS_DRY_RUN", True), \
                mock.patch("urllib.request.urlopen") as urlopen:
            sid = handler.send_sms(sms_payload())

        urlopen.assert_not_called()
        self.assertTrue(sid.startswith("dry-run-"))


if __name__ == "__main__":
    unittest.main()
