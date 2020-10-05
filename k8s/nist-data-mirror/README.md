# NIST Data Mirror Helm Chart

A simple helm chart to deploy NIST mirror in k8s.

The intended purpose of this helm chart is to be able to deploy NIST mirror in k8s. Anyone who is using k8s to deploy services and tools, can use this and get started.

## Pre-requsite

This chart will not create docker image, but rather deploy it in k8s. Please create image first and push it to your docker repo. You can use [docker-build.sh] script to create docker image.

## Usage

### Packaging

```sh
# cd to charts directory
$ cd nist-data-mirror

# Create directory for package
$ mkdir ./target

# Package helm chart
$ helm package --app-version <app_version>  --version <helm_chart_version> -destination ./target .

# As a best practice, push your packaged chart to helm repo. e.g. push it to artifactory, chartmusuem etc.
$ helm push target/nist-data-mirror-<helm_chart_version>.tgz chartmuseum
```

### Deploying

You can have your k8s cluster anywhere (EKS, EKG, private cluster, etc), But you need to be able connect to k8s cluster to run this. For more details please check how to use [helm for deployments].

```sh
# helm install --name <release_name> <helm_repo>/<chart_name>
$ helm install --name nist-data-mirror chartmuseum/nist-data-mirror
```

The httpd server will take a minute to spin up as it is mirroring the initial NVD files.

### Using as proxy

Once deployment is complete, you should have nist-mirror running as service in k8s. If you created a service as type "loadBalancer", you can try and check loadBalancer endpoint to test it. Please check on details about how to use [LoadBalancers in K8S]

### Copyright & License

nist-data-mirror is Copyright (c) Steve Springett. All Rights Reserved.

Dependency-Check is Copyright (c) Jeremy Long. All Rights Reserved.

Permission to modify and redistribute is granted under the terms of the Apache 2.0 license. See the [LICENSE] [Apache 2.0] file for the full license.

[OWASP Dependency-Check](https://www.owasp.org/index.php/OWASP_Dependency_Check)
[Apache 2.0](https://github.com/stevespringett/nist-data-mirror/blob/master/LICENSE)
[docker-build.sh](https://github.com/stevespringett/nist-data-mirror/blob/master/docker-build.sh)
[LoadBalancers in K8S](https://kubernetes.io/docs/concepts/services-networking/)
[helm for deployments](https://helm.sh/docs/using_helm/)
