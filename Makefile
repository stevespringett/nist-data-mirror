#!/usr/bin/make -f

SHELL = /bin/bash
.SHELLFLAGS = -ec

OPS_ACCOUNT_ID ?= 216871930768

# Container Settings, adjust as necessary
DESIRED_COUNT ?= 1
MEMORY_MIN ?= 256
MEMORY_MAX ?= 512
FUNCTION_ALIAS ?= prd


clean:
	mvn clean
	@rm -f aws/roles/deploy-ecs-container/files/handler.zip

build: clean
	mvn package
	java -jar target/nist-data-mirror.jar nist
	docker build -t ops-microservice-nginx-nist-data-mirror .

build-lambda:
	# Not currently storing lambda in artifact management
	cd lb_map && zip -r ../handler.zip __init__.py lambda_function.py python && cd -
	mv handler.zip aws/roles/deploy-ecs-container/files/handler.zip

.ONESHELL:
deployci: build-lambda
	if [ "$(ARTIFACT_NAME)" == "" ]
	then
	 	echo "Error: ARTIFACT_NAME is required"
		exit 1
	fi
	HEALTH_CHECK_PATH='/'
	DEPLOY_DATE=$$(date -u +"%Y-%m-%dT%H:%M:%SZ")

	ansible-playbook -v aws/deploy-ecs-container.yml -e "
	opsaccountid=$(OPS_ACCOUNT_ID)
	awsaccountid=$(AWS_ACCOUNT_ID)
	ecrname=$(ECRNAME)
	containertag=$(ARTIFACT_NAME)
	containername=$(CONTAINERNAME)
	desiredcount=$(DESIRED_COUNT)
	memorymin=$(MEMORY_MIN)
	memorymax=$(MEMORY_MAX)
	healthcheckpath='$${HEALTH_CHECK_PATH}'
	region=$(AWS_REGION)
	accountname=$(ACCOUNT_NAME)
	product=$(PRODUCT)
	tagenvironment=$(ENV)
	tagenvironmentnumber=$(ENV_NO)
	cluster_env_no=$(CLUSTER_ENV_NO)
	deploydate=$${DEPLOY_DATE}
	loglevel=info
	functionalias=$(FUNCTION_ALIAS)
	jenkins_workspace="$(WORKSPACE)"
	version="$(ARTIFACT_NAME)"
	"

.PHONY: clean test build buildci deploy deployci compileci
