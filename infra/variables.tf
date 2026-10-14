variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and tags."
  type        = string
  default     = "dev"
}

variable "image_tag" {
  description = "Tag of the Pulse API image in ECR to run on ECS (the deploy workflow passes the commit SHA)."
  type        = string
  default     = "latest"
}

variable "vpc_cidr" {
  description = "CIDR block for the Pulse VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "app_desired_count" {
  description = "Number of API tasks to run on ECS."
  type        = number
  default     = 2
}

variable "app_cpu" {
  description = "Fargate CPU units for each API task."
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "Fargate memory (MiB) for each API task."
  type        = number
  default     = 1024
}

variable "db_instance_class" {
  description = "RDS instance class for PostgreSQL."
  type        = string
  default     = "db.t4g.micro"
}

variable "redis_node_type" {
  description = "ElastiCache node type for Redis."
  type        = string
  default     = "cache.t4g.micro"
}

variable "sqs_max_receive_count" {
  description = "Deliveries before a message moves to its dead-letter queue. Must match MAX_RECEIVE_COUNT in the email Lambda."
  type        = number
  default     = 3
}

variable "ses_sender" {
  description = "Verified SES address the email Lambda sends from."
  type        = string
  default     = "notifications@pulse.dev"
}

variable "lambda_build_dir" {
  description = "Directory containing handler.py plus its pip-installed dependencies (built by the deploy workflow)."
  type        = string
  default     = "../build/email-consumer"
}

variable "log_retention_days" {
  description = "CloudWatch log retention for the API and Lambda."
  type        = number
  default     = 14
}
