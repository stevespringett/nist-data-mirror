[![Build Status](https://travis-ci.org/stevespringett/nist-data-mirror.svg?branch=master)](https://travis-ci.org/stevespringett/nist-data-mirror)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/21c46e93bdbe4e6f99085da9ece477e3)](https://www.codacy.com/app/stevespringett/nist-data-mirror?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=stevespringett/nist-data-mirror&amp;utm_campaign=Badge_Grade)
[![License](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)][Apache 2.0]

NIST Data Mirror
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

To use a proxy provide http.proxyHost / http.proxyPort system properties.

Downloading
----------------

If you do not wish to download sources and compile yourself, [pre-compiled binaries] are available 
for use. NIST Data Mirror is also available on the Maven Central Repository.

```xml
<dependency>
    <groupId>us.springett</groupId>
    <artifactId>nist-data-mirror</artifactId>
    <version>1.3.0</version>
</dependency>
```

Docker
----------------

A Dockerfile is included, and the image is available on Docker Hub as [sspringett/nvdmirror](https://hub.docker.com/r/sspringett/nvdmirror). This was created to 
assist in debugging other issues. While the image does create an httpd instance 
that mirrors the NVD CVE data feeds - note that it also creates a backup for all 
changed files and there is currently no automatic cleanup.

```
$ mvn clean package
$ docker build --rm -t sspringett/nvdmirror .
$ mkdir target/docs
$ docker run -dit \
  --name mirror \
  -p 80:8080 \
  --mount type=bind,source="$(pwd)"/target/docs/,target=/usr/local/apache2/htdocs \
  sspringett/nvdmirror
```

The httpd server will take a minute to spin up as it is mirroring the initial NVD files.

To use a proxy during build time provide the `http_proxy`, `https_proxy` and `no_proxy` 
environment variables as build arguments (e.g. `--build-arg http_proxy="${http_proxy}"`.
For the runtime you can pass the `http.proxyHost` and `http.proxyPort` values as environment variables (`proxy_host`, `proxy_port`).

The image is designed to be runned as a random non-root user and can be deployed on
container orchestration platforms such as Kubernetes and OpenShift.

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
