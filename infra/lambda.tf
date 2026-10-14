# Python email consumer (lambda/email-consumer). SQS invokes it in batches and
# it reports per-message failures so only failed messages are retried.

data "archive_file" "email_consumer" {
  type        = "zip"
  source_dir  = var.lambda_build_dir
  output_path = "${path.module}/.build/email-consumer.zip"
}

resource "aws_cloudwatch_log_group" "email_consumer" {
  name              = "/aws/lambda/${local.name}-email-consumer"
  retention_in_days = var.log_retention_days
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "email_consumer" {
  name               = "${local.name}-email-consumer"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

# Logs plus the network interfaces a VPC-attached Lambda needs.
resource "aws_iam_role_policy_attachment" "email_consumer_vpc" {
  role       = aws_iam_role.email_consumer.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

data "aws_iam_policy_document" "email_consumer" {
  statement {
    sid = "ConsumeEmailQueue"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:ChangeMessageVisibility",
    ]
    resources = [aws_sqs_queue.channel["EMAIL"].arn]
  }

  statement {
    sid       = "SendEmail"
    actions   = ["ses:SendEmail", "ses:SendRawEmail"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "email_consumer" {
  name   = "email-consumer"
  role   = aws_iam_role.email_consumer.id
  policy = data.aws_iam_policy_document.email_consumer.json
}

resource "aws_lambda_function" "email_consumer" {
  function_name    = "${local.name}-email-consumer"
  role             = aws_iam_role.email_consumer.arn
  runtime          = "python3.12"
  handler          = "handler.handler"
  filename         = data.archive_file.email_consumer.output_path
  source_code_hash = data.archive_file.email_consumer.output_base64sha256
  timeout          = 20
  memory_size      = 256

  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }

  environment {
    variables = {
      DB_HOST     = aws_db_instance.postgres.address
      DB_PORT     = tostring(aws_db_instance.postgres.port)
      DB_NAME     = aws_db_instance.postgres.db_name
      DB_USER     = aws_db_instance.postgres.username
      DB_PASSWORD = random_password.db.result
      REDIS_HOST  = aws_elasticache_cluster.redis.cache_nodes[0].address
      REDIS_PORT  = tostring(aws_elasticache_cluster.redis.port)
      SES_SENDER  = var.ses_sender
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.email_consumer,
    aws_iam_role_policy_attachment.email_consumer_vpc,
  ]
}

resource "aws_lambda_event_source_mapping" "email_queue" {
  event_source_arn        = aws_sqs_queue.channel["EMAIL"].arn
  function_name           = aws_lambda_function.email_consumer.arn
  batch_size              = 10
  function_response_types = ["ReportBatchItemFailures"]
}
