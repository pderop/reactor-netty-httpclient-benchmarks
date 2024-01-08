#!/bin/bash

#!/bin/bash

if [ $# -ne 5 ]; then
  echo "Usage: $0 <backend-ip> <backend-port> <maxconn> <concurrent-streams> <work stealing (true/false)>"
  exit 1
fi

BACKEND_IP=$1
BACKEND_PORT=$2
MAXCONN=$3
CONC_STREAMS=$4
STEAL=$5

java -version

echo "get/steal=$STEAL"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dbackend.host=$BACKEND_IP -Dbackend.port=$BACKEND_PORT -Dduration=5 \
  -Dh2client.maxconn=$MAXCONN -Dh2client.maxstreams=$CONC_STREAMS \
  -Dsteal=$STEAL -Dnocoloc=false -Dscenario=get org.example.ClientApp

echo
echo "post/steal=$STEAL"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dbackend.host=$BACKEND_IP -Dbackend.port=$BACKEND_PORT -Dduration=5 \
  -Dh2client.maxconn=$MAXCONN -Dh2client.maxstreams=$CONC_STREAMS \
  -Dsteal=$STEAL -Dnocoloc=false -Dscenario=post org.example.ClientApp
