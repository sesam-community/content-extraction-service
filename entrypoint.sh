#!/usr/bin/env sh
set -e

if [ "$1" = "sh" ]; then
    exec "$@"
else
    exec java -Dorg.slf4j.simpleLogger.defaultLogLevel=${LOGLEVEL:=info} -jar /srv/content-extraction-service-1.0-SNAPSHOT.jar
fi
