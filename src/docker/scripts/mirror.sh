#!/bin/sh

files=`java -jar /usr/local/bin/nist-data-mirror.jar /tmp/nvd | grep -Eo 'Uncompressing.*' | grep -Eo '[^ ]*\.gz'`

timestamp=$(date +%s)

for f in $files
do
  echo "/tmp/nvd/$f"
  if [ -f "/tmp/nvd/$f" ]; then
    if [ ! -d "/usr/local/apache2/htdocs/backup/$timestamp" ]; then
      mkdir -p "/usr/local/apache2/htdocs/backup/$timestamp"
    fi
    if [ -f "/usr/local/apache2/htdocs/$f" ]; then
      mv "/usr/local/apache2/htdocs/$f" "/usr/local/apache2/htdocs/backup/$timestamp/"
    fi
    cp -f "/tmp/nvd/$f" "/usr/local/apache2/htdocs/$f"
  fi
done
