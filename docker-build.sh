#!/usr/bin/env sh
docker build --no-cache=true \
             --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
             --build-arg http_proxy="${http_proxy}" \
             --build-arg https_proxy="${https_proxy}" \
             --build-arg no_proxy="${no_proxy}" \
             -t sspringett/nvdmirror .
