# reactor-netty-httpclient-benchmarks

Various benchmarks for Reactor Netty Http Client

## Prerequisites:
First build the following projects and publish them into your local M2:

```
git checkout -b workstealing-pool git@github.com:pderop/reactor-pool.git
cd reactor-pool
./gradlew publishToMavenLocal

git checkout -b workstealing-pool git@github.com:pderop/reactor-netty.git
cd reactor-netty
./gradlew publishToMavenLocal
```

## Build

build the project with jdk21 (the gradle wrapper is not installed, you need to set it up using `gradle wrapper`)

## benchmarks descriptions

The samples are made up of three jars (ideally to be installed on three different machines):

- gatling/build/libs/gatling-1.0.0-all.jar
- frontend/build/libs/frontend-1.0.0.jar
- backend/build/libs/backend-1.0.0.jar

Gatling -> frontend -> backend

In Gatling, there are three scenarios: "Get", "Post", and "Post2"
- Get: a small HTTP/2 GET request is sent to the frontend, which forwards it to the backend
- Post: same as above, but with POST with a 1k json payload in both request and response
- Post2: a more complex scenario (the json payload is parsed by the frontend):

```mermaid
sequenceDiagram
    participant Gating
    participant Frontend
    participant Backend

    Gating->>Frontend: POST(json 1K)
    Frontend-->>Backend: GET /checkPermission
    Backend->>Frontend: 200 OK
    Frontend->>Backend: POST(json 1K)
    Backend-->>Frontend: 200 OK (with json 1K)
    Frontend-->>Gating: 200 OK (json 1K)
```

In addition to Gatling, there is a also a loader based on reactor netty HttpClient, with two supported scenarios: "get" and "post".

The benchmarks can be run in three mode:

- normal mode: The frontend uses the Reactor Netty Http2 client in order to forward requests to the backend
- non-colocated mode: the frontend http2 client is configured with a custom Event Loop Group with colocation disabled, like this:
```
    client = client.runOn(LoopResources.create("client-loops", 1, Runtime.getRuntime().availableProcessors(), true, false));
```
- work stealing mode: the frontend Http2Client uses an experimental (work in progress) work stealing reactor pool, where streams acquisition 
are handled by concurrent pools, each one being executed within its own event loop executor.
(there is one actual Http2Pool dedicated to each HttpClient event loop). 

## Start the servers

- start the backend (it will listen on 8080 port by default):
```
java -Dbackend.port=8080 -jar backend/build/libs/backend-1.0.0.jar
```

- start a frontend server in normal mode on port 8090
```
java -Dfrontend.port=8090 -Dbackend.host=BACKEND_IP -jar frontend/build/libs/frontend-1.0.0.jar
```

- start a frontend server in non-colocated mode on port 8091
```
java -Dfrontend.port=8091 -Dnocoloc=true -Dbackend.host=BACKEND_IP -jar frontend/build/libs/frontend-1.0.0.jar
```

- start a frontend server in work stealing mode mode on port 8092
```
java -Dfrontend.port=8092 -Dsteal=true -Dbackend.host=BACKEND_IP -jar frontend/build/libs/frontend-1.0.0.jar
```

## Start Gatling

To bench whatever frontend, use the -Dfrontend.port parameter to select the right frontend port to test:
set FRONTEND_HOST and FRONTEND_PORT to either 8090, 8091, or 8092.

The following example establishes 1 single HTTP/2 connection with concurrent streams=1000:

```
 rm -rf test-reports; 
 java -Dduration=60s \
    -Dsteps=1 \
    -Dh2.concurrency=1000 \
    -Dincrement=1 \
    -Dlevel.lasting=1s \
    -Dramp.lasting=1s \
    -Dfrontend.host=$FRONTEND_HOST \
    -Dfrontend.port=$FRONTEND_PORT \
    -jar gatling-1.0.0-all.jar test Get Post Post2
```

The following example gradually establishes 5 HTTP/2 connection with concurrent streams=200:
```
rm -rf test-reports; 
java -Dduration=60s \
    -Dsteps=5 \
    -Dincrement=1 \
    -Dh2.concurrency=200 \
    -Dlevel.lasting=1s \
    -Dramp.lasting=1s \
    -Dfrontend.host=$FRONTEND_HOST \
    -Dfrontend.port=$FRONTEND_PORT \
    -jar gatling-1.0.0-all.jar test Get Post Post2
```

The following example gradually establishes 10 HTTP/2 connection with concurrent streams=100:
```
rm -rf test-reports; 
java -Dduration=60s \
    -Dsteps=2 \
    -Dincrement=5 \
    -Dh2.concurrency=100 \
    -Dlevel.lasting=1s \
    -Dramp.lasting=1s \
    -Dfrontend.host=$FRONTEND_HOST \
    -Dfrontend.port=$FRONTEND_PORT \
    -jar gatling-1.0.0-all.jar test Get Post Post2
```

The following example gradually establishes 1000 HTTP/2 connection with concurrent streams=10:
```
rm -rf test-reports; 
java -Dduration=60s \
    -Dsteps=10 \
    -Dincrement=100 \
    -Dh2.concurrency=10 \
    -Dlevel.lasting=1s \
    -Dramp.lasting=1s \
    -Dfrontend.host=$FRONTEND_HOST \
    -Dfrontend.port=$FRONTEND_PORT \
    -jar gatling-1.0.0-all.jar test Get Post Post2
```

## Or start h2load

alternatively, instead of using gatling, you can use **h2load**. First, install it:

on linux:
```
sudo apt-get install nghttp2-client
```

on macos:
```
brew install nghttp2
```

now, run the scenario with the run-h2load.sh script (the current working directory must be the toplevel project (where this README is located):
```
Usage: ./scripts/run-h2load.sh <frontend host> <frontend port> <frontend nocoloc port> <frontend workstealing port> <nb connections> <concurrent streams per connection>
```

The following will open 1 HTTP/2 connections with max concurrent streams=1000
```
./scripts/run-h2load.sh $FRONTEND_HOST 8090 8091 8092 1 1000
```
In the above example, the script will test the three frontends (on port 8090, 8091, 8092), and will use 1 http2 connections during tests, with max-concurrent-streams=1000.

The following will open 5 HTTP/2 connections with max concurrent streams=200 for each connection:
```
./scripts/run-h2load.sh $FRONTEND_HOST 8090 8091 8092 5 200
```

The following will open 100 HTTP/2 connections with max concurrent streams=10 for each connection:
```
./scripts/run-h2load.sh $FRONTEND_HOST 8090 8091 8092 100 10
```

## Start the reactor netty HttpClient loader:

there is a standalone reactor http client netty in the frontend, use the `scripts/run-client.sh` script:

```
Usage: ./scripts/run-client.sh <backend-host> <backend-port> <maxconn> <concurrent-streams> <work stealing flag (true/false)"
```

The following example configures the client with 1 HTTP/2 connection, 1000 cucurrent streams, and without work stealing
```
./scripts/run-client.sh $BACKEND_HOST 8080 10 100 false
```

The following example configures the client with 1 HTTP/2 connection, 1000 cucurrent streams, and without work stealing
```
./scripts/run-client.sh 169.254.120.63 8080 10 100 true
```

## notes: 

## known issues
