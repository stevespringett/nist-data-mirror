#!/bin/sh

mkdir target/docs
docker run -dit \
  --name mirror \
  -p 80:80 \
  --mount type=bind,source="$(pwd)"/target/docs/,target=/usr/local/apache2/htdocs \
  sspringett/nvdmirror