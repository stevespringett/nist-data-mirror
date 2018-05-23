from __future__ import print_function

import os
import json
import logging
import boto3
logger = logging.getLogger()
logger.setLevel(logging.INFO)

client = boto3.client('ecs')
client_elbv2 = boto3.client('elbv2')

def lambda_handler(event, context):
    # Cleanup the ECS service name by removing 'service:'
    service_name = event['detail']['group']
    service_name = service_name.replace('service:','')
    logger.info('ECS service = {}'.format(service_name))
    logger.info('Task status = {}'.format(event['detail']['containers'][0]['lastStatus']))

    # Only run if service is running or stopped
    if (service_name == str(os.environ['ECS_SERVICE']) and (event['detail']['containers'][0]['lastStatus'] == 'RUNNING' or event['detail']['containers'][0]['lastStatus'] == 'STOPPED')):
        cluster_arn = event['detail']['clusterArn']

        # Return service arns
        logger.info('Return the list of task arns from the ecs service;')
        task_list = client.list_tasks(
            cluster=cluster_arn,
            serviceName = service_name
        )['taskArns']
        logger.info(task_list)

        # Return the dynamic ports and instance id from the tasks
        logger.info('Return dynamic ports & instanceId from ecs task;')
        host_ports = {}
        for taskarn in task_list:
            task = client.describe_tasks(
                cluster=cluster_arn,
                tasks=[
                    taskarn,
                ]
            )
            ec2_instance_arn = client.describe_container_instances(
                cluster=cluster_arn,
                containerInstances=[
                    task['tasks'][0]['containerInstanceArn'],
                ]
            )
            for network_binding in task['tasks'][0]['containers'][0]['networkBindings']:
                host_ports.update({network_binding['hostPort']: ec2_instance_arn['containerInstances'][0]['ec2InstanceId']})
        logger.info(host_ports)
        
        # Return the target group arn
        logger.info('Return target group arn')
        target_group_arn = client_elbv2.describe_target_groups(
            Names=[
                "{}".format(str(os.environ['TARGET_GROUP'])),
            ]
        )['TargetGroups'][0]['TargetGroupArn']
        logger.info(target_group_arn)
        
        # Return the existing ports on the target group
        logger.info('Return existing ports on target group;')
        targets = client_elbv2.describe_target_health(
            TargetGroupArn=target_group_arn
        )['TargetHealthDescriptions']
        existing_ports = {target['Target']['Port']: target['Target']['Id'] for target in targets}
        logger.info(existing_ports)
        
        new_ports = []
        obsolete_ports = []
        
        # Create a list of ports on the target group that do not match the task
        for existing_port, instance_id in existing_ports.items():
            if not existing_port in host_ports:
                obsolete_ports.append({'Id': instance_id, 'Port': existing_port})
        logger.info('Obsolete ports to remove from target group;')
        logger.info(obsolete_ports)
        
        # Create a list of new ports to add to the target group
        for host_port, instance_id in host_ports.items():
            if not host_port in existing_ports:
                new_ports.append({'Id': instance_id, 'Port': host_port})
        logger.info('New ports to add to target group;')
        logger.info(new_ports)
        
        # Add new ports to target group
        if new_ports:
            client_elbv2.register_targets(
                TargetGroupArn=target_group_arn,
                Targets=new_ports
            )
        
        # Remove old ports from target group
        if obsolete_ports:
            client_elbv2.deregister_targets(
                TargetGroupArn=target_group_arn,
                Targets=obsolete_ports
            )
