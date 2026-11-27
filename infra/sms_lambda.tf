# Python SMS consumer (lambda/sms-consumer). Same shape as the email consumer:
# SQS invokes it in batches and it reports per-message failures. It sends
# through Twilio, which it reaches over the internet via the NAT gateway.
# With no Twilio credentials set, it runs in dry-run mode and logs instead.

data "archive_file" "sms_consumer" {
  type        = "zip"
  source_dir  = var.sms_lambda_build_dir
  output_path = "${path.module}/.build/sms-consumer.zip"
}

resource "aws_cloudwatch_log_group" "sms_consumer" {
  name              = "/aws/lambda/${local.name}-sms-consumer"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "sms_consumer" {
  name               = "${local.name}-sms-consumer"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "sms_consumer_vpc" {
  role       = aws_iam_role.sms_consumer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "sms_consumer" {
  statement {
    sid = "ConsumeSmsQueue"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ChangeMessageVisibility",
    ]
    resources = [aws_sqs_queue.channel["SMS"].arn]
  }
}

resource "aws_iam_role_policy" "sms_consumer" {
  name   = "sms-consumer"
  role   = aws_iam_role.sms_consumer.id
  policy = data.aws_iam_policy_document.sms_consumer.json
}

resource "aws_lambda_function" "sms_consumer" {
  function_name    = "${local.name}-sms-consumer"
  role             = aws_iam_role.sms_consumer.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.sms_consumer.output_path
  source_code_hash = data.archive_file.sms_consumer.output_base64sha256
  timeout          = 20
  memory_size      = 256

  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      DB_HOST            = aws_db_instance.postgres.address
      DB_PORT            = tostring(aws_db_instance.postgres.port)
      DB_NAME            = aws_db_instance.postgres.db_name
      DB_USER            = aws_db_instance.postgres.username
      DB_PASSWORD        = random_password.db.result
      REDIS_HOST         = aws_elasticache_cluster.redis.cache_nodes[0].address
      REDIS_PORT         = tostring(aws_elasticache_cluster.redis.port)
      TWILIO_ACCOUNT_SID = var.twilio_account_sid
      TWILIO_AUTH_TOKEN  = var.twilio_auth_token
      TWILIO_FROM_NUMBER = var.twilio_from_number
      SMS_DRY_RUN        = var.twilio_account_sid == "" ? "true" : "false"
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.sms_consumer,
    aws_iam_role_policy_attachment.sms_consumer_vpc,
  ]
}

resource "aws_lambda_event_source_mapping" "sms_queue" {
  event_source_arn        = aws_sqs_queue.channel["SMS"].arn
  function_name           = aws_lambda_function.sms_consumer.arn
  batch_size              = 10
  function_response_types = ["ReportBatchItemFailures"]
}
