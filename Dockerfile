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
LABEL org.label-schema.vcs-url="https://github.com/ballerinalang/container-support"
LABEL org.label-schema.vendor="sspringett"
LABEL org.label-schema.version=$BUILD_VERSION
LABEL org.label-schema.docker.cmd="docker run -dit --name mirror -p 80:80 --mount type=bind,source=\"$(pwd)\"/target/docs/,target=/usr/local/apache2/htdocs sspringett/nvdmirror"

ENV user=mirror

RUN apk update                                                              && \
    apk add --no-cache openjdk8-jre dcron nss                               && \
    mkdir -p /var/log/cron                                                  && \
    mkdir -p /var/spool/cron/crontabs                                       && \
    mkdir /tmp/nvd                                                          && \
    touch /var/log/cron.log                                                 && \
    chgrp -R 0 /var                                                         && \
    chmod -R g=u /var                                                       && \
    chgrp -R 0 /usr/                                                        && \
    chmod -R g=u /usr                                                       && \
    chgrp -R 0 /tmp                                                         && \
    chmod -R g=u /tmp                                                       

RUN echo "Include conf/mirror.conf"                                         
COPY /src/docker/scripts/* /
COPY /src/docker/crontab/* /var/spool/cron/crontabs/mirror
COPY /src/docker/conf/mirror.conf /usr/local/apache2/conf
COPY /target/nist-data-mirror.jar /usr/local/bin/

EXPOSE 8080/tcp

ENTRYPOINT ["/entry.sh"]
CMD ["/cmd.sh"]
USER 1001
