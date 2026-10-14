locals {
  name     = "pulse-${var.environment}"
  azs      = slice(data.aws_availability_zones.available.names, 0, 2)
  channels = ["EMAIL", "SMS"]
}
