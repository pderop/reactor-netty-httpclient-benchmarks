#!/bin/bash

if [ $# -ne 5 ]; then
  echo "Usage: $0 <frontend-port> <backend-host> <maxconn> <no-colocation-flag> <workstealing-flag>"
  exit 1
fi

FRONTEND_PORT=$1
BACKEND_HOST=$2
MAXCONN=$3
NOCOLOC=$4
STEAL=$5

java -version
echo "MAXCONN=$MAXCONN, STEAL=$STEAL, NO COLOCATION=$NOCOLOC"

java -Dh2client.maxconn=$MAXCONN \
  -Dfrontend.port=$FRONTEND_PORT \
  -Dbackend.host=$BACKEND_HOST \
  -Dsteal=$STEAL \
  -Dnocoloc=$NOCOLOC \
  -jar frontend/build/libs/frontend-1.0.0.jar

