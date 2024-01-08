#!/bin/bash

if [ $# -ne 6 ]; then
  echo "Usage: $0 <frontend port> <backend ip> <ioworkers> <maxconn> <nocoloc flag> <workstealing flag>"
  exit 1
fi

PORT=$1
BACKEND_IP=$2
IOWORKERS=$3
MAXCONN=$4
NOCOLOC=$5
STEAL=$6

java -version
echo "IOWORKERS=$IOWORKERS, MAXCONN=$MAXCONN, STEAL=$STEAL, NOCOLOC=$NOCOLOC"

java -Dh2client.maxconn=$MAXCONN -Dreactor.netty.ioWorkerCount=$IOWORKERS -Dfrontend.port=$PORT -Dbackend.host=$BACKEND_IP -Dsteal=$STEAL -Dnocoloc=$NOCOLOC -jar frontend/build/libs/frontend-1.0.0.jar

