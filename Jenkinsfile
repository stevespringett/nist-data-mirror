#!groovy
@Library('shared') _
ansiColor {
    node {
        // Add environment variables
        env.SLACK_CHANNEL='#build-cde'
        // Set Jenkins job properties
        properties([
            buildDiscarder(logRotator(numToKeepStr: '20')),
            // Run at 1am every day AEST (3pm UST)
            pipelineTriggers([cron('H 15 * * *')])
        ])
        try {
            stage('Checkout SCM & Setup Workspace') {
                scmUtils.checkout()
                // Download CICD Config from Artifactory for OPs
                opsAwsAccountVars = loadCloudConfig.inventory('ops')
                echo "OPs account set to ${opsAwsAccountVars.inventory.prd.aws_account_id}"
                echo "OPs default region set to ${opsAwsAccountVars.inventory.prd.regions.default_region}"
            }
            slack.sendNotification('STARTED')
            stage('Setup S3 & Route53') {
                def HostedZone = 'ops.nxbos.cloud'
                def MirrorName = 'nist-mirror'
                def PublicHostedZoneId = sh(returnStdout: true, script: "aws --region ${opsAwsAccountVars.inventory.prd.regions.default_region} " +
                    "route53 list-hosted-zones --query \"HostedZones[?Name == '${HostedZone}.']\" " +
                    "| jq -r '.[] | select(.Config.PrivateZone==false).Id' | awk -F'/' '{print \$3}'").trim()
                def PrivateHostedZoneId = sh(returnStdout: true, script: "aws --region ${opsAwsAccountVars.inventory.prd.regions.default_region} " +
                    "route53 list-hosted-zones --query \"HostedZones[?Name == '${HostedZone}.']\" " +
                    "| jq -r '.[] | select(.Config.PrivateZone==true).Id' | awk -F'/' '{print \$3}'").trim()
                withAWS(region: "${opsAwsAccountVars.inventory.prd.regions.default_region}") {
                    cfnUpdate(stack: "ops-prd-0-${MirrorName}", 
                        file:'aws/setup_nist_mirror.yml',
                        params:['PublicHostedZoneId': PublicHostedZoneId, 'PrivateHostedZoneId': PrivateHostedZoneId, 
                            'HostedZone': HostedZone, 'MirrorPrefix': MirrorName]
                    )
                }
            }
            stage('Build jar') {
                if (!fileExists('target/nist-data-mirror.jar')) {
                    withMaven(maven: 'maven') {
                        sh('mvn clean package')
                    }
                }
            }
            stage('Download NIST files') {
                sh('java -jar target/nist-data-mirror.jar nist-mirror')
            }
            stage('Copy NIST mirror to S3') {
                echo 'Copying NIST mirror to S3'
                    sh("aws --region ${opsAwsAccountVars.inventory.prd.regions.default_region} s3 sync --acl public-read ./nist-mirror/ s3://nist-mirror.ops.nxbos.cloud")
            }
            slack.sendNotification('SUCCESS')
        }
        catch (err) {
            slack.sendNotification('FAILED')
            throw err
        }
        finally {}
    }
}
