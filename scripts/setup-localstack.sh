#!/bin/bash
set -e

ENDPOINT=http://localstack:4566
REGION=us-east-1

echo "Waiting for LocalStack..."
until curl -s $ENDPOINT/_localstack/health | grep -q '"sns": "available"'; do
  sleep 2
done
echo "LocalStack ready."

# ── Dead-Letter Queues ────────────────────────────────────────────────────────
echo "Creating DLQs..."

EMAIL_DLQ=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs create-queue \
  --queue-name email-dlq \
  --query 'QueueUrl' --output text)

SMS_DLQ=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs create-queue \
  --queue-name sms-dlq \
  --query 'QueueUrl' --output text)

EMAIL_DLQ_ARN=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs get-queue-attributes \
  --queue-url $EMAIL_DLQ --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

SMS_DLQ_ARN=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs get-queue-attributes \
  --queue-url $SMS_DLQ --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

# ── Main Queues ───────────────────────────────────────────────────────────────
echo "Creating main queues..."

EMAIL_QUEUE=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs create-queue \
  --queue-name email-queue \
  --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$EMAIL_DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" \
  --query 'QueueUrl' --output text)

SMS_QUEUE=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs create-queue \
  --queue-name sms-queue \
  --attributes "{\"VisibilityTimeout\":\"30\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$SMS_DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}" \
  --query 'QueueUrl' --output text)

EMAIL_QUEUE_ARN=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs get-queue-attributes \
  --queue-url $EMAIL_QUEUE --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

SMS_QUEUE_ARN=$(aws --endpoint-url=$ENDPOINT --region=$REGION sqs get-queue-attributes \
  --queue-url $SMS_QUEUE --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

# ── SNS Topic ─────────────────────────────────────────────────────────────────
echo "Creating SNS topic..."

TOPIC_ARN=$(aws --endpoint-url=$ENDPOINT --region=$REGION sns create-topic \
  --name pulse-notifications \
  --query 'TopicArn' --output text)

# ── SNS Subscriptions with filter policies ────────────────────────────────────
echo "Subscribing queues to topic..."

aws --endpoint-url=$ENDPOINT --region=$REGION sns subscribe \
  --topic-arn $TOPIC_ARN \
  --protocol sqs \
  --notification-endpoint $EMAIL_QUEUE_ARN \
  --attributes '{"FilterPolicy":"{\"channel\":[\"EMAIL\"]}"}'

aws --endpoint-url=$ENDPOINT --region=$REGION sns subscribe \
  --topic-arn $TOPIC_ARN \
  --protocol sqs \
  --notification-endpoint $SMS_QUEUE_ARN \
  --attributes '{"FilterPolicy":"{\"channel\":[\"SMS\"]}"}'

echo ""
echo "Infrastructure ready."
echo "Topic ARN:        $TOPIC_ARN"
echo "Email queue URL:  $EMAIL_QUEUE"
echo "SMS queue URL:    $SMS_QUEUE"
echo "Email DLQ URL:    $EMAIL_DLQ"
echo "SMS DLQ URL:      $SMS_DLQ"
