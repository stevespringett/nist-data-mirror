#!groovy
import net.nxbos.jenkins.workflow.stages.CheckoutSCM
import net.nxbos.jenkins.workflow.PipelineUtils

@Library('shared') _

String repoName = env.JOB_NAME.tokenize("/")[1]
List<String> parts = repoName.tokenize("-")
String productName = parts.first()
String resource = parts[1]
String componentName = null
if (parts[3]) { componentName = parts[3..-1].join("-") }

config = [
    slackChannel: "build-cde",
    repoName: repoName,
    product: productName,
    resource: resource,
    componentName: componentName
]

properties()

Void properties() {
    // Set Jenkins job properties
    def imageList = pipelineChoices.ecrImageListByBranch(config.repoName, env.BRANCH_NAME)
    imageList.add(0, 'build')

    properties([
        buildDiscarder(logRotator(numToKeepStr: '20')),
        pipelineTriggers([cron('0 18 * * *')]), // 4am AEST/5am AEDT
        parameters([
            choice(name: 'ACCOUNT_NAME', choices: ['ops','opsandbox'].join('\n'), description: 'Asset Name'),
            choice(name: 'ENV', choices: ['prd', 'dev'].join('\n'), description: 'Environment'),
            string(name: 'ENV_NO', defaultValue: '0', description: 'Environment number'),
            string(name: 'CLUSTER_ENV_NO', defaultValue: '0', description: 'Cluster environment number? (default 0)'),
            string(name: 'CREDSTASH_NO', defaultValue: '0', description: 'Credstash kms number? (default 0)'),
            choice(name: 'ARTIFACT_NAME', choices: imageList.join('\n'), description: ''),
            choice(name: 'AWS_REGION', choices: PipelineUtils.regions.join('\n'), description: 'AWS Region to build/deploy'),
        ])
    ])
}

ansiColor {
    timestamps {
        isStartedByTimer = jobCauses.isCancellableBuild('', 'automated')
        // Trigger regression if adhoc kicked off, or by timer (only on develop)
        if ( !isStartedByTimer || (isStartedByTimer && env.BRANCH_NAME == 'master') ) {
            node {
                try {
                    String utcDate = sh(returnStdout: true, script: "date -u +'%Y%m%dT%H%M%SZ'").trim()

                    stage('Checkout SCM & Setup Environment', CheckoutSCM.getInstance().getStage(this))
                    if ( params.ARTIFACT_NAME == 'build' ) {
                        stage('Build & Test') {
                            // Increment version
                            sh("ls -l")
                            // Strip forward slash as its illegal in docker tags (% char as well)
                            def encodedBranchName = env.BRANCH_NAME.replace("/","--")
                            gitShortRef = sh (returnStdout: true, script: "git rev-parse --short HEAD").trim()
                            // Download CICD Config from Artifactory for OPs
                            opsAwsAccountVars = loadCloudConfig.inventory('ops')
                            opsRegion = opsAwsAccountVars.inventory.prd.regions.default_region
                            opsAccountId = opsAwsAccountVars.inventory.prd.aws_account_id
                            echo "INFO: Ops default region set to ${opsRegion}"

                            sh ("chmod -R 777 .git")
                            sh ("zip -r ${config.repoName} . -x '*nbos-cloud-config*'")

                            echo "Copying repo source to S3"
                            String codebuildSource = sh (returnStdout: true, script:"aws --region ${opsRegion} " +
                                "codebuild batch-get-projects --names ${config.repoName} --query \"projects[0].source.location\" --output text"
                                ).trim().replace("arn:aws:s3:::", "")
                            String s3Bucket = codebuildSource.split("/")[0]
                            sh ("aws --region ${opsRegion} s3 cp ${WORKSPACE}/${config.repoName}.zip s3://${codebuildSource}")

                            // Set new artifact name and copy build reports from S3 to Jenkins workspace
                            // Not used for commons-spring libraries
                            ecrBuildTag = "build_${gitShortRef}_${config.repoName}_Branch-${encodedBranchName}"
                            def codeBuildResult = null
                            codeBuildResult = awsCodeBuild projectName: "${config.repoName}",
                                region: "${opsRegion}",
                                sourceControlType: 'project',
                                credentialsType: 'keys',
                                awsAccessKey: env.AWS_ACCESS_KEY_ID,
                                awsSecretKey: env.AWS_SECRET_ACCESS_KEY,
                                envVariables: """[
                                        {IMAGE_TAG, ${ecrBuildTag}},
                                        {AWS_DEFAULT_REGION, ${params.AWS_REGION}},
                                        {AWS_REGION, ${params.AWS_REGION}},
                                        {AWS_ACCOUNT_ID, ${opsAccountId}},
                                        {ECRNAME, ${config.repoName}},
                                        {BRANCH_NAME, ${encodedBranchName}},
                                        {ENVIRONMENT_NUMBER, 1},
                                        {ENVIRONMENT_NAME, bld},
                                        {IMAGE_REPO_NAME, ${opsAccountId}.dkr.ecr.${opsRegion}.amazonaws.com}
                                        ]"""
                        }
                        stage('Publish to ECR') {
                            // If feature branch, set ecr build tag as artifact name and exit
                            if ( env.BRANCH_NAME != 'develop' ) {
                                jobParams.addChoice("ARTIFACT_NAME", ecrBuildTag)
                                jobParams.setParam("ARTIFACT_NAME", ecrBuildTag)
                                return
                            } else {
                                /*
                                * Tag ECR image with new version tag format
                                * This image tag is re-constructed in manifestUtils.groovy (Any change will be required in both places)
                                */
                                def manifest = sh(returnStdout: true,
                                    script: "aws --region ${params.AWS_REGION} ecr batch-get-image " +
                                        "--repository-name ${config.repoName}  " +
                                        "--image-ids imageTag=${ecrBuildTag} " +
                                        "--query images[].imageManifest --output text").trim()

                                sh("aws --region ${params.AWS_REGION} ecr put-image " +
                                    "--repository-name ${config.repoName} " +
                                    "--image-tag 'v${utcDate}_${gitShortRef}_${config.repoName}' " +
                                    "--image-manifest '${manifest}'")

                                jobParams.addChoice("ARTIFACT_NAME", "v${utcDate}_${gitShortRef}_${config.repoName}")
                                jobParams.setParam("ARTIFACT_NAME", "v${utcDate}_${gitShortRef}_${config.repoName}")
                            }
                        }
                    } else {
                        skippedStages()
                    }
                }
                catch (err) {
                    throw err
                }
            }
            if ( params.ARTIFACT_NAME != 'build' ) {
                node {
                    try {
                        stage('Deploy NIST Mirror') {
                            opsAwsAccountVars = loadCloudConfig.inventory('ops')
                            withEnv([ "AWS_REGION=${params.AWS_REGION}", "AWS_ACCOUNT_ID=${opsAwsAccountVars.inventory."${params.ENV}".aws_account_id}",
                                "CONTAINERNAME=${config.componentName}", "ACCOUNT_NAME=${params.ACCOUNT_NAME}", "PRODUCT=${config.product}",
                                "ECRNAME=${config.repoName}", "ARTIFACT_NAME=${params.ARTIFACT_NAME}","ENV_NO=${params.ENV_NO}", "ENV=${params.ENV}",
                                "CLUSTER_ENV_NO=${params.CLUSTER_ENV_NO}", "CREDSTASH_NO=${params.CREDSTASH_NO}"]) {
                                try {
                                    sh 'make deployci'
                                } catch (Exception e) {
                                    helpECS.showUsefulHelp(params.ENV, params.ENV_NO, config.product, config.componentName, params.ACCOUNT_NAME)
                                    throw e
                                }
                            }
                        }
                    } catch (err) {
                        throw err
                    } finally {
                        deleteDir()
                    }
                }
            }
        }
    }
}

def skippedStages() {
    stage('Build & Test') {echo 'INFO: Skipped stage, deploying existing artifact'}
    stage('Publish to ECR') {echo 'INFO: Skipped stage, deploying existing artifact'}
}