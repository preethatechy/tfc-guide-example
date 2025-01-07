# Copyright (c) HashiCorp, Inc.
# SPDX-License-Identifier: MPL-2.0

output "instance_ami" {
  value = aws_instance.ubuntu.ami
}

output "instance_arn" {
  value = aws_instance.ubuntu.arn
}


def checkAppInfraDeploy(config) { def isAppDeploy = !readConfigFile().component.releaseComponents.isEmpty() return config.executeCreateInfrastructure && !config.executeDestroyInfrastructure ? (isAppDeploy ? pipelineConstants.stages.CREATE_INFRA_AND_APP_DEPLOY : pipelineConstants.stages.CREATE_INFRA) : config.executeDestroyInfrastructure && !config.executeCreateInfrastructure ? pipelineConstants.stages.DESTROY_INFRA : config.executeCreateInfrastructure && config.executeDestroyInfrastructure ? (isAppDeploy ? pipelineConstants.stages.CREATE_AND_DESTROY_INFRA_AND_APP_DEPLOY : pipelineConstants.stages.CREATE_AND_DESTROY_INFRA) : null }
