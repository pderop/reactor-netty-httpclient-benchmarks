#!/bin/bash

if [ $# -ne 4 ]; then
  echo "Usage: $0 <frontend port> <backend ip> <nocoloc flag> <workstealing flag>)"
  exit 1
fi

PORT=$1
BACKEND_IP=$2
NOCOLOC=$3
STEAL=$4

java -version
java -Dfrontend.port=$PORT -Dbackend.host=$BACKEND_IP -Dsteal=$STEAL -Dnocoloc=$NOCOLOC -jar frontend/build/libs/frontend-1.0.0.jar

