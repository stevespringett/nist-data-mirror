[![Build Status](https://travis-ci.org/stevespringett/nist-data-mirror.svg?branch=master)](https://travis-ci.org/stevespringett/nist-data-mirror)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/21c46e93bdbe4e6f99085da9ece477e3)](https://www.codacy.com/app/stevespringett/nist-data-mirror?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=stevespringett/nist-data-mirror&amp;utm_campaign=Badge_Grade)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][Apache 2.0]

Service Usage
================== 

To use this service configure your pom.xml to reference the mirror at [https://nist-nvd-mirror.ops.nxbos.cloud](https://nist-nvd-mirror.ops.nxbos.cloud)
This service is accessible from:

* **Versent Melb HQ**
* **via OpenVPN**

```xml
<plugin>
  <groupId>org.owasp</groupId>
  <artifactId>dependency-check-maven</artifactId>
  <version>3.1.1</version>
  <configuration>
    <failBuildOnCVSS>7</failBuildOnCVSS>
    <format>${dependency-check-format}</format>
    <suppressionFiles>https://nist-nvd-mirror.ops.nxbos.cloud/suppressions.xml</suppressionFiles>
    <cveUrl12Modified>https://nist-nvd-mirror.ops.nxbos.cloud/nvdcve-modified.xml.gz</cveUrl12Modified>
    <cveUrl20Modified>https://nist-nvd-mirror.ops.nxbos.cloud/nvdcve-2.0-modified.xml.gz</cveUrl20Modified>
    <cveUrl12Base>https://nist-nvd-mirror.ops.nxbos.cloud/nvdcve-%d.xml</cveUrl12Base>
    <cveUrl20Base>https://nist-nvd-mirror.ops.nxbos.cloud/nvdcve-2.0-%d.xml</cveUrl20Base>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

NBOS Deployment
==================

For the NBOS environment we have added deployment files ontop of the original forked repo.

These consist of:

* Jenkinsfile
* Makefile
* Dockerfile
* Ansible
* AWS Serverless Application Model

## Requirements

### Local Dev Requirements

* Ansible (>2.3)
* GNUMake (>4.2.1)
* Docker (>18.03.1-ce)

### NBOS Infra requirements

* Ops/AWS Account provisioned with a Jenkins server and ECS Cluster [nbos-cloud-jenkins-docker](https://github.com/transurbantech/nbos-cloud-jenkins-docker)
* External and Internal loadbalancers in place [ops-base-infra](https://github.com/transurbantech/ops-base-infra)

## Building

### Local

```sh
make build
```

### Jenkins

```html
https://jenkins-apps.ops.nxbos.cloud/job/transurbantech/job/ops-microservice-nginx-nist-nvd-mirror/
```

Build process runs the jar as per original repo documentation, downloading all files into a temporary `nist/` directory.
It then builds a nginx docker container hosting the files under `nist/` and `nginx_hosted_files/` under root

When built by jenkins, this will include publishing the docker container to an ECR repo for deployment.

## Deployment

This repo deploys the following infrastructure

* ECS Service with task definition (pulling ECR Image)
* Service Log Group (not used currently)
* External target group (attached to ext application lb)
* External listener rule based on host-header
* External hosted zone route 53 domain `nist-nvd-mirror.ops.nxbos.cloud/`
* Internal target group (attached to int application lb)
* Internal listener rule based on host-header
* Internal hosted zone route 53 domain `nist-nvd-mirror.ops.nxbos.cloud/`
* Lambda to map ecs task/service to 2nd loadbalancer (ext lb)

The Lambda is required due to AWS not supporting registering 2 loadbalancer against a single service. The SAM Template links the internal loadbalancer to the service automatically. The lambda does the external link based on ecs event triggers.

NIST Data Mirror (Original Repo documentation)
================

A simple Java command-line utility to mirror the NVD (CPE/CVE XML and JSON) data from NIST.

The intended purpose of nist-data-mirror is to be able to replicate the NIST vulnerabiity data 
inside a company firewall so that local (faster) access to NIST data can be achieved.

nist-data-mirror does not rely on any third-party dependencies, only the Java SE core libraries. 
It can be used in combination with [OWASP Dependency-Check] in order to provide Dependency-Check 
a mirrored copy of NIST data.

For best results, use nist-data-mirror with cron or another scheduler to keep the mirrored data fresh.

Usage
----------------

### Building

```sh
mvn clean package
```

### Running

```sh
java -jar nist-data-mirror.jar <mirror-directory> [xml|json]
```
Omitting filetype argument will result in both filetypes being downloaded.

Downloading
----------------

If you do not wish to download sources and compile yourself, [pre-compiled binaries] are available 
for use. NIST Data Mirror is also available on the Maven Central Repository.

```xml
<dependency>
    <groupId>us.springett</groupId>
    <artifactId>nist-data-mirror</artifactId>
    <version>1.2.0</version>
</dependency>
```

Related Projects
----------------

* [VulnDB Data Mirror](https://github.com/stevespringett/vulndb-data-mirror)

Copyright & License
-------------------

nist-data-mirror is Copyright (c) Steve Springett. All Rights Reserved.

Dependency-Check is Copyright (c) Jeremy Long. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] [Apache 2.0] file for the full license.

  [OWASP Dependency-Check]: https://www.owasp.org/index.php/OWASP_Dependency_Check
  [Apache 2.0]: https://github.com/stevespringett/nist-data-mirror/blob/master/LICENSE
  [pre-compiled binaries]: https://github.com/stevespringett/nist-data-mirror/releases
