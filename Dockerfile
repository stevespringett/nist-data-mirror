FROM httpd:alpine

MAINTAINER Jeremy Long <jeremy.long@gmail.com>

RUN apk update                                                              && \
    apk add --no-cache openjdk8 dcron nano                                      && \
    mkdir -p /var/log/cron                                                  && \
    mkdir -m 0644 -p /var/spool/cron/crontabs                               && \
    touch /var/log/cron.log                                                 && \
    mkdir -m 0644 -p /etc/cron.d                                            && \
    mkdir /tmp/nvd                                                          && \
    mkdir /usr/local/apache2/htdocs/backup                                  && \
    rm -rf /var/lib/apt/lists/* /tmp/*

RUN echo "Include conf/mirror.conf"                                         
COPY /src/docker/scripts/* /
COPY /src/docker/crontab/* /etc/cron.d/
COPY /src/docker/conf/mirror.conf /usr/local/apache2/conf
COPY /target/nist-data-mirror.jar /usr/local/bin/

ENTRYPOINT ["/entry.sh"]
CMD ["/cmd.sh"]
