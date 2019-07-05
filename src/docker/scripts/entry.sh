#!/bin/sh

# Listen on port 8080 for non-privileged users
sed -i 's/Listen 80/Listen 8080/g' /usr/local/apache2/conf/httpd.conf 

exec "$@"
