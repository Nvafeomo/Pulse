# SNS fan-out: one topic, one SQS queue (plus DLQ) per channel, each
# subscribed with a filter policy on the `channel` message attribute.
# Mirrors scripts/setup-localstack.sh.

resource "aws_sns_topic" "notifications" {
  name = "${local.name}-notifications"
}

resource "aws_sqs_queue" "dlq" {
  for_each = toset(local.channels)

  name                      = "${local.name}-${lower(each.key)}-dlq"
  message_retention_seconds = 1209600 # 14 days, the SQS maximum
}

resource "aws_sqs_queue" "channel" {
  for_each = toset(local.channels)

  name                       = "${local.name}-${lower(each.key)}-queue"
  visibility_timeout_seconds = 30

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = var.sqs_max_receive_count
  })
}

# Allow the SNS topic (and only that topic) to deliver into each queue.
data "aws_iam_policy_document" "sns_to_sqs" {
  for_each = toset(local.channels)

  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.channel[each.key].arn]

    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }

    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.notifications.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "channel" {
  for_each = toset(local.channels)

  queue_url = aws_sqs_queue.channel[each.key].id
  policy    = data.aws_iam_policy_document.sns_to_sqs[each.key].json
}

resource "aws_sns_topic_subscription" "channel" {
  for_each = toset(local.channels)

  topic_arn     = aws_sns_topic.notifications.arn
  protocol      = "sqs"
  endpoint      = aws_sqs_queue.channel[each.key].arn
  filter_policy = jsonencode({ channel = [each.key] })

  # The email Lambda expects the SNS envelope, so raw delivery stays off.
  raw_message_delivery = false
}
