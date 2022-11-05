FROM httpd:alpine

ARG BUILD_DATE
ARG BUILD_VERSION

ARG http_proxy
ARG https_proxy
ARG no_proxy

# Labels.
LABEL maintainer="jeremy.long@gmail.com"
LABEL name="sspringett/nvdmirror"
LABEL version=$BUILD_VERSION
LABEL org.label-schema.schema-version="1.0"
LABEL org.label-schema.build-date=$BUILD_DATE
LABEL org.label-schema.name="sspringett/nvdmirror"
LABEL org.label-schema.description="NIST Data Mirror"
LABEL org.label-schema.url="https://github.com/stevespringett/nist-data-mirror"
LABEL org.label-schema.vcs-url="https://github.com/stevespringett/nist-data-mirror"
LABEL org.label-schema.vendor="sspringett"
LABEL org.label-schema.version=$BUILD_VERSION
LABEL org.label-schema.docker.cmd="docker run -dit --name mirror -p 80:80 --mount type=bind,source=\"$(pwd)\"/target/docs/,target=/usr/local/apache2/htdocs sspringett/nvdmirror"

RUN apk update                                               && \
    apk add --no-cache openjdk8-jre dcron nss supervisor     && \
    mkdir -p /tmp/nvd                                        && \
    rm -v /usr/local/apache2/htdocs/index.html

COPY ["/src/docker/conf/supervisord.conf", "/etc/supervisor/conf.d/supervisord.conf"]
COPY ["/src/docker/scripts/mirror.sh", "/mirror.sh"]
COPY ["/src/docker/crontab/mirror", "/etc/crontabs/mirror"]
COPY ["/src/docker/conf/mirror.conf", "/usr/local/apache2/conf"]
COPY ["/target/nist-data-mirror.jar", "/usr/local/bin/"]

EXPOSE 80/tcp

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/conf.d/supervisord.conf", "-l", "/var/log/supervisord.log", "-j", "/var/run/supervisord.pid"]
