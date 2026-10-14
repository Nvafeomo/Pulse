output "api_url" {
  description = "Public URL of the Pulse API load balancer."
  value       = "http://${aws_lb.app.dns_name}"
}

output "ecr_repository_url" {
  description = "ECR repository the deploy workflow pushes API images to."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "sns_topic_arn" {
  value = aws_sns_topic.notifications.arn
}

output "queue_urls" {
  description = "Per-channel SQS queue URLs."
  value       = { for channel, queue in aws_sqs_queue.channel : channel => queue.url }
}
