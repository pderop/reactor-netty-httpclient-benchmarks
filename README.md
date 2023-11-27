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

- to bench whatever frontend, use the -Dfrontend.port parameter to select the right frontend port to test:
set FRONTEND_HOST and FRONTEND_PORT to either 8090, 8091, or 8092:
```
java -Dsteps=10 -Dfrontend.host=FRONTEND_HOST -Dfrontend.port=FRONTEND_PORT-jar gatling/build/libs/gatling-1.0.0-all.jar test-report-name Get Post
```
`-Dsteps` is used to configure the number of connections to establish. Do not use too much connections, it's HTTP/2 ! 
For the **Post2** scenario, there is an issue, and you must not use more than 5 connections (reduce it if you see any failures):
```
java -Dsteps=5 -Dfrontend.host=FRONTEND_HOST -Dfrontend.port=FRONTEND_PORT -jar gatling/build/libs/gatling-1.0.0-all.jar test-report-name Post2
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
Usage: ./scripts/run-h2load.sh <frontend ipaddr> <backend ipaddr> <frontend port> <frontend nocoloc port> <frontend workstealing port> <nb connections>
./scripts/run-h2load.sh FRONTEND_IP BACKEND_IP 8090 8091 8092 10
```
In the above example, the script will test the three frontends (on port 8090, 8091, 8092), and will use 10 http2 connections during tests, with max-concurrent-streams=100.

## Start the reactor netty HttpClient loader:

there is a standalone reactor http client netty in the frontend, just invoke it like this:

```
java -cp frontend/build/libs/frontend-1.0.0.jar -Dsteal=true|false -Dbackend.host=BACKEND_IP -Dduration=5 -Dscenario=get|post org.example.ClientApp
```
for the **-Dscenario** option, set it either to `get` or `post`
for the **-Dsteal** option, set it either to `false` or `true`

## notes: 

## known issues
