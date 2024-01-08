#!/bin/bash

if [ $# -ne 6 ]; then
  echo "Usage: $0 <frontend ipaddr> <frontend port> <frontend nocoloc port> <frontend workstealing port> <nb connections> <concurrent streams>"
  exit 1
fi

FRONTEND=$1
FRONTEND_PORT=$2
FRONTEND_NOCOLOC_PORT=$3
FRONTEND_STEAL_PORT=$4
NB_CONN=$5
CONC_STREAMS=$6

run() {
  desc=$1
  frontend_port=$2
  ncnx=$3
  method=$4
  msg_per_cnx=${5:-$CONC_STREAMS}

  echo
  echo "$desc"

  if [ "$method" = "get" ]; then
    h2load -w16 -W16 --warm-up-time=5 --duration=8 -c${ncnx} -m${msg_per_cnx} -p h2c https://$FRONTEND:$frontend_port/get \
      | grep -e "finished in" -e "requests\:"
  else
    h2load -w16 -W16 --warm-up-time=5 --duration=8 -c${ncnx} -m${msg_per_cnx} --data=gatling/src/gatling/resources/user.json --header="Content-Type: application/json" \
      https://$FRONTEND:$frontend_port/$method \
      | grep -e "finished in" -e "requests\:"
  fi
}

## Get scenario

run "--- Frontend/get/$NB_CONN/coloc" $FRONTEND_PORT $NB_CONN get
run "--- Frontend/get/$NB_CONN/nocoloc" $FRONTEND_NOCOLOC_PORT $NB_CONN get
run "--- Frontend/get/$NB_CONN/workstealing" $FRONTEND_STEAL_PORT $NB_CONN get

### Post scenario
#
run "--- Frontend/post/$NB_CONN" $FRONTEND_PORT $NB_CONN post
run "--- Frontend/post/$NB_CONN/nocoloc" $FRONTEND_NOCOLOC_PORT $NB_CONN post
run "--- Frontend/post/$NB_CONN/workstealing" $FRONTEND_STEAL_PORT $NB_CONN post
#
### Post2 scenario
#
run "---  Frontend/post2/$NB_CONN" $FRONTEND_PORT $NB_CONN post2
run "---  Frontend/post2/$NB_CONN/nocoloc" $FRONTEND_NOCOLOC_PORT $NB_CONN post2
run "---  Frontend/post2/$NB_CONN/workstealing" $FRONTEND_STEAL_PORT $NB_CONN post2

