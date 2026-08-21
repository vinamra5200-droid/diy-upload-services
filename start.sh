#!/bin/sh
set -e

echo "Starting application..."
exec java ${JAVA_OPTS} -jar /app/app.jar "$@"
