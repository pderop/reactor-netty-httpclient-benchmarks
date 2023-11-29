#!/bin/bash

if [ $# -ne 6 ]; then
  echo "Usage: $0 <frontend ipaddr> <backend ipaddr> <frontend port> <frontend nocoloc port> <frontend workstealing port> <nb connections>"
  exit 1
fi

FRONTEND=$1
BACKEND=$2
FRONTEND_PORT=$3
FRONTEND_NOCOLOC_PORT=$4
FRONTEND_STEAL_PORT=$5
NB_CONN=$6

# the frontend that is not configured with -Dnocoloc=true should have all the pool's connection colocated, let's force that setup.
h2load -c1 -m100 -n100 https://$FRONTEND:$FRONTEND_PORT/get >/dev/null

run() {
  desc=$1
  frontend_port=$2
  ncnx=$3
  method=$4
  msg_per_cnx=${5:-100}

  echo
  echo "$desc"

  if [ "$method" = "get" ]; then
    h2load -w16 -W16 --warm-up-time=10 --duration=10 -c${ncnx} -m${msg_per_cnx} -p h2c https://$FRONTEND:$frontend_port/get \
      | grep -e "finished in" -e "requests\:"
  else
    h2load -w16 -W16 --warm-up-time=10 --duration=5 -c${ncnx} -m${msg_per_cnx} --data=gatling/src/gatling/resources/user.json --header="Content-Type: application/json" \
      https://$FRONTEND:$frontend_port/$method \
      | grep -e "finished in" -e "requests\:"
  fi
}

## Get scenario

run "--- Frontend/get/$NB_CONN" $FRONTEND_PORT $NB_CONN get
run "--- Frontend/get/$NB_CONN/nocoloc" $FRONTEND_NOCOLOC_PORT 1 get
run "--- Frontend/get/$NB_CONN/workstealing" $FRONTEND_STEAL_PORT 1 get


## Post scenario

run "--- Frontend/post/$NB_CONN" $FRONTEND_PORT $NB_CONN post
run "--- Frontend/post/$NB_CONN/nocoloc" $FRONTEND_NOCOLOC_PORT $NB_CONN post
run "--- Frontend/post/$NB_CONN/workstealing" $FRONTEND_STEAL_PORT $NB_CONN post

## Post2 scenario

run "---  Frontend/post2/$nb_conn" $FRONTEND_PORT $NB_CONN post2
run "---  Frontend/post2/$nb_conn/nocoloc" $FRONTEND_NOCOLOC_PORT $NB_CONN post2
run "---  Frontend/post2/$nb_conn/workstealing" $FRONTEND_STEAL_PORT $NB_CONN post2

echo
echo "--- HttpClient/get"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dbackend.host=$BACKEND -Dduration=5 -Dscenario=get org.example.ClientApp

echo
echo "--- HttpClient/get/nocoloc"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dnocoloc=true -Dbackend.host=$BACKEND -Dduration=5 -Dscenario=get org.example.ClientApp

echo
echo "--- HttpClient/get/workstealing"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dsteal=true -Dbackend.host=$BACKEND -Dduration=5 -Dscenario=get org.example.ClientApp

echo
echo "--- HttpClient/post"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dbackend.host=$BACKEND -Dduration=5  -Dscenario=post org.example.ClientApp

echo
echo "--- HttpClient/post/nocoloc"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dnocoloc=true -Dbackend.host=$BACKEND -Dduration=5 -Dscenario=post org.example.ClientApp

echo
echo "--- HttpClient/post/workstealing"
java -cp frontend/build/libs/frontend-1.0.0.jar -Dsteal=true -Dbackend.host=$BACKEND -Dduration=5 -Dscenario=post org.example.ClientApp


