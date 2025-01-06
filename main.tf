# Copyright (c) HashiCorp, Inc.
# SPDX-License-Identifier: MPL-2.0

def validatePhaseConfig(config) {
    def validPhases = ["DEV", "LLE", "PROD"]

    // Ensure both `phase` and `tfeOrganization` are provided
    if (!config.phase || !validPhases.contains(config.phase.toUpperCase())) {
        error "Invalid or missing phase: ${config.phase}. Use [DEV, LLE, PROD]."
    }

    if (!config.tfeOrganization) {
        error "TFE Organization is not specified in the configuration."
    }

    // Check if the organization name matches the phase
    def phaseOrgMap = ["DEV": "DEV", "LLE": "UAT", "PROD": "PROD"]
    def validOrg = config.tfeOrganization.toUpperCase().contains(phaseOrgMap[config.phase.toUpperCase()] ?: "PROD")

    if (!validOrg) {
        printErrLog("Invalid environment/phase combination for ${config.phase}.")
    }

    return validOrg
}



provider "aws" {
  region = var.region
}

data "aws_ami" "ubuntu" {
  most_recent = true

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-focal-20.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }

  owners = ["099720109477"] # Canonical
}

resource "aws_instance" "ubuntu" {
  ami           = data.aws_ami.ubuntu.id
  instance_type = var.instance_type

  tags = {
    Name = var.instance_name
  }
}
