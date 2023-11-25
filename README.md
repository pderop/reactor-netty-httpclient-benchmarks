# reactor-netty-httpclient-benchmarks

Gatling benchmarks for Reactor Netty Http Client

## Prerequisites:
You need to first build the following projects and publish them to local M2 before building this project:

```
git checkout -b workstealing-pool git@github.com:reactor/reactor-pool.git 
cd reactor-pool
./gradlew publishToMavenLocal

git checkout -b workstealing-pool git@github.com:reactor/reactor-netty.git 
cd reactor-netty
./gradlew publishToMavenLocal
```

(for the moment, the "workstealing-pool is not yet created on the reactor-poo/reactor-netty projects)

## Build

build the project with jdk21 (the gradle wrapper is not installed, you need to set it up using `gradlew wrapper`)

## benchmarks descriptions

The samples are made up of three jars (ideally to be installed on three different machines):

- gatling/build/libs/gatling-1.0.0-all.jar
- frontend/build/libs/frontend-1.0.0.jar
- backend/build/libs/backend-1.0.0.jar

Gatling -> frontend -> backend

In Gatling, there are three scenarios: "Get", "Post", and "Post2"
- Get: a small GET request is sent to the frontend, which forwards it to the backend
- Post: same as Get, but with Post with a 1k json payload in both request and response
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

In addition to Gatling, there is a also a loader based on reactor netty HttpClient, with two supported scenarions: "get" and "post".

The benchmarks can be run in three mode:

- normal mode: the Reactor Netty Http2 client is used by the frontend in order to forward requests to the backend
- non-colocated mode: when Gatling begins injecting requests, there's a race condition. The first HTTP/2 connection 
that receives potentially many multiplexed requests triggers the establishment of connections (because at this point the pool is empty). 
These connections are then managed by the event loop of the first HTTP/2 connection that has received the initial requests.
This setup results in all connections managed by the HTTP/2 connection pool being handled by a single event loop. 
So, in this case, we can configure the Http2Client in the frontend with a custom event loop group without colocation support, 
that seems to greatly improve performances:
```
    client = client.runOn(LoopResources.create("client-loops", 1, Runtime.getRuntime().availableProcessors(), true, false));
```
- work stealing mode: the Http2Client streams acquisition will be handled by concurrent sub pools, each one being executed 
by the HttpClient event loops.

To run the scenarios in normal mode:
```
java -Dsteal=false -Dbackend.host=BACKEND_IP  -jar frontend-1.0.0.jar
java -jar backend-1.0.0.jar
java -Dsteps=10 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Get Post
java -Dsteps=7 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Post2
java -Dsteal=false -Dscenario=get  -Dbackend.host=BACKEND_IP -cp frontend-1.0.0.jar org.example.ClientApp
java -Dsteal=false -Dscenario=post -Dbackend.host=BACKEND_IP -cp frontend-1.0.0.jar org.example.ClientApp
```

To run the scenarios without colocation:
```
java -Dnocoloc=true -Dsteal=false -Dbackend.host=BACKEND_IP  -jar frontend-1.0.0.jar
java -jar backend-1.0.0.jar
java -Dsteps=10 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Get Post
java -Dsteps=7 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Post2
java -Dsteal=false -Dscenario=get  -Dbackend.host=BACKEND_IP -cp frontend-1.0.0.jar org.example.ClientApp
java -Dsteal=false -Dscenario=post -Dbackend.host=BACKEND_IP -cp frontend-1.0.0.jar org.example.ClientApp
```

To run the scenarios with work stealing enabled:

```
java -Dsteal=true -Dbackend.host=BACKEND_IP -jar frontend-1.0.0.jar
java -jar backend-1.0.0.jar
java -Dsteps=10 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Get Post
java -Dsteps=7 -Dfrontend.host=FRONTEND_IP -jar gatling-1.0.0-all.jar test Post2
java -Dsteal=true -Dbackend.host=BACKEND_IP -Dscenario=get -cp frontend-1.0.0.jar org.example.ClientApp
java -Dsteal=true -Dbackend.host=BACKEND_IP -Dscenario=post -cp frontend-1.0.0.jar org.example.ClientApp
```

the `-Dsteps` in gatling specifies the number of http2 connections established to the frontend.
For the "Post2" scenario, reduce the number of connections if exceptions are observed (see "issues" sections)

## Current benchmarks results

### 11/24/2023:

(on private home network, TODO: do the same on GCP):

|                          | Default mode (reqs/sec) | Colocation disabled (reqs/sec) | Steal (reqs/sec) |
|--------------------------|-------------------------|--------------------------------|---------------|
| **Gatling/Get/5 cnx**    | 45757.933               | 77667.383                      | 104146.533    |
| **Gatling/Get/10 cnx**   | 45202.683               | 92010.433                      | 123405.867    |
| **Gatling/Get/100 cnx**  | 45597.033               | 99688.733                      | 128446.017    |
| **Gatling/Post 5 cnx**   | 32880.867               | 60109.85                       | 70396.7       |
| **Gatling/Post 10 cnx**  | 32592.917               | 70077.833                      | 77083.56      |
| **Gatling/Post 100 cnx** | 32655.55                | 72261.017                      | 79337.65      |
| **Gatling/Post2 3 cnx**  | 16560.633               | 34169.733                      | 43625.9       |
| **Gatling/Post2 5 cnx**  | 16854.233               | 36829.717                      | 49287.217     |
| **Gatling/Post2 7 cnx**  | 16301.2                 | 41194.233                      | 54004.433     |
| **HttpClient/get**       | 303924                  | 307604                         | 340056        |
| **HttpClient/post**      | 174496                  | 178216                         | 199797        |

#### notes: 

- for the Post2 scenario, the max connections must not be too high, else many exceptions
are throwns and the frontend looses it's backend connections. See below the #issue 1.
using -Dsteps=7 seems fine, maybe you'll need to reduce it.

- both HttpClient/get, and HttpClient/post tests are getting the same kind of results whether default is used, or if
colocation is disabled. It's because the HttpClient is used from the main() thread, not from a reactor netty event loop,
so there is no colocation in this case ...


## known issues

##### issue 1
When Gatling opens too much connections, we can get the following exceptions when running the **Post2** scenario 
This can happen in all modes, whether work stealing mode is used or not. Maybe a bug in the scenario, I don't know for 
the moment.

in the backend:
<details>
  <summary>io.netty.handler.codec.http2.Http2Exception: Stream 774273 does not exist for inbound frame RST_STREAM, endOfStream = false</summary>
14:57:52.480 [reactor-http-nio-10] WARN  i.n.channel.DefaultChannelPipeline - An exceptionCaught() event was fired, and it reached at the tail of the pipeline. It usually means the last handler in the pipeline did not handle the exception.
io.netty.handler.codec.http2.Http2Exception: Stream 774273 does not exist for inbound frame RST_STREAM, endOfStream = false
	at io.netty.handler.codec.http2.Http2Exception.connectionError(Http2Exception.java:109)
	at io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder$FrameReadListener.verifyStreamMayHaveExisted(DefaultHttp2ConnectionDecoder.java:696)
	at io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder$FrameReadListener.onRstStreamRead(DefaultHttp2ConnectionDecoder.java:455)
	at io.netty.handler.codec.http2.DefaultHttp2FrameReader.readRstStreamFrame(DefaultHttp2FrameReader.java:509)
	at io.netty.handler.codec.http2.DefaultHttp2FrameReader.processPayloadState(DefaultHttp2FrameReader.java:259)
	at io.netty.handler.codec.http2.DefaultHttp2FrameReader.readFrame(DefaultHttp2FrameReader.java:159)
	at io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder.decodeFrame(DefaultHttp2ConnectionDecoder.java:188)
	at io.netty.handler.codec.http2.DecoratingHttp2ConnectionDecoder.decodeFrame(DecoratingHttp2ConnectionDecoder.java:63)
	at io.netty.handler.codec.http2.DecoratingHttp2ConnectionDecoder.decodeFrame(DecoratingHttp2ConnectionDecoder.java:63)
	at io.netty.handler.codec.http2.Http2ConnectionHandler$FrameDecoder.decode(Http2ConnectionHandler.java:393)
	at io.netty.handler.codec.http2.Http2ConnectionHandler.decode(Http2ConnectionHandler.java:453)
	at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:529)
	at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:468)
	at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:290)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at reactor.netty.http.server.HttpServerConfig$H2ChannelMetricsHandler.channelRead(HttpServerConfig.java:811)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at io.netty.handler.ssl.SslHandler.unwrap(SslHandler.java:1475)
	at io.netty.handler.ssl.SslHandler.decodeJdkCompatible(SslHandler.java:1338)
	at io.netty.handler.ssl.SslHandler.decode(SslHandler.java:1387)
	at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:529)
	at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:468)
	at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:290)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
	at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
	at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
	at io.netty.channel.nio.AbstractNioByteChannel$NioByteUnsafe.read(AbstractNioByteChannel.java:166)
	at io.netty.channel.nio.NioEventLoop.processSelectedKey(NioEventLoop.java:788)
	at io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized(NioEventLoop.java:724)
	at io.netty.channel.nio.NioEventLoop.processSelectedKeys(NioEventLoop.java:650)
	at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:562)
	at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)
</details>

and many exceptions like the following in the frontend:

<details>
  <summary>reactor.core.publisher.Operators - Error while discarding element from a Collection, continuing with next element</summary>
14:59:19.444 [reactor-http-nio-2] WARN  reactor.core.publisher.Operators - Error while discarding element from a Collection, continuing with next element
io.netty.util.IllegalReferenceCountException: refCnt: 0, decrement: 1
	at io.netty.util.internal.ReferenceCountUpdater.toLiveRealRefCnt(ReferenceCountUpdater.java:83)
	at io.netty.util.internal.ReferenceCountUpdater.release(ReferenceCountUpdater.java:148)
	at io.netty.buffer.AbstractReferenceCountedByteBuf.release(AbstractReferenceCountedByteBuf.java:101)
	at io.netty.buffer.AbstractDerivedByteBuf.release0(AbstractDerivedByteBuf.java:98)
	at io.netty.buffer.AbstractDerivedByteBuf.release(AbstractDerivedByteBuf.java:94)
	at reactor.core.publisher.Operators.lambda$discardLocalAdapter$0(Operators.java:389)
	at java.base/java.util.function.Consumer.lambda$andThen$0(Consumer.java:65)
	at reactor.core.publisher.Operators.onDiscardMultiple(Operators.java:570)
	at reactor.core.publisher.MonoCollectList$MonoCollectListSubscriber.onError(MonoCollectList.java:106)
	at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
	at reactor.core.publisher.FluxMap$MapSubscriber.onError(FluxMap.java:134)
	at reactor.core.publisher.MonoFlatMapMany$FlatMapManyInner.onError(MonoFlatMapMany.java:255)
	at reactor.netty.channel.FluxReceive.onInboundError(FluxReceive.java:465)
	at reactor.netty.channel.ChannelOperations.onInboundError(ChannelOperations.java:515)
	at reactor.netty.http.client.HttpClientOperations.onInboundClose(HttpClientOperations.java:324)
	at reactor.netty.channel.ChannelOperationsHandler.channelInactive(ChannelOperationsHandler.java:73)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:305)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:281)
	at io.netty.channel.AbstractChannelHandlerContext.fireChannelInactive(AbstractChannelHandlerContext.java:274)
	at io.netty.channel.DefaultChannelPipeline$HeadContext.channelInactive(DefaultChannelPipeline.java:1405)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:301)
	at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:281)
	at io.netty.channel.DefaultChannelPipeline.fireChannelInactive(DefaultChannelPipeline.java:901)
	at io.netty.handler.codec.http2.AbstractHttp2StreamChannel$Http2ChannelUnsafe$2.run(AbstractHttp2StreamChannel.java:791)
	at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
	at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
	at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
	at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:566)
	at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
	at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
	at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
	at java.base/java.lang.Thread.run(Thread.java:1583)
14:59:19.444 [reactor-http-nio-2] ERROR r.n.http.server.HttpServerOperations - [5a85beff/172421-1, L:/127.0.0.1:8090 - R:/127.0.0.1:58523] Error starting response. Replying error status
reactor.netty.http.client.PrematureCloseException: Connection prematurely closed DURING response

io.netty.channel.ChannelPipelineException: io.netty.handler.codec.http2.Http2FrameCodec.handlerRemoved() has thrown an exception.
at io.netty.channel.DefaultChannelPipeline.callHandlerRemoved0(DefaultChannelPipeline.java:640)
at io.netty.channel.DefaultChannelPipeline.destroyDown(DefaultChannelPipeline.java:876)
at io.netty.channel.DefaultChannelPipeline.destroyUp(DefaultChannelPipeline.java:844)
at io.netty.channel.DefaultChannelPipeline.destroy(DefaultChannelPipeline.java:836)
at io.netty.channel.DefaultChannelPipeline.access$700(DefaultChannelPipeline.java:46)
at io.netty.channel.DefaultChannelPipeline$HeadContext.channelUnregistered(DefaultChannelPipeline.java:1392)
at io.netty.channel.AbstractChannelHandlerContext.invokeChannelUnregistered(AbstractChannelHandlerContext.java:215)
at io.netty.channel.AbstractChannelHandlerContext.invokeChannelUnregistered(AbstractChannelHandlerContext.java:195)
at io.netty.channel.DefaultChannelPipeline.fireChannelUnregistered(DefaultChannelPipeline.java:821)
at io.netty.channel.AbstractChannel$AbstractUnsafe$7.run(AbstractChannel.java:821)
at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
at io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:566)
at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
at java.base/java.lang.Thread.run(Thread.java:1583)
Caused by: io.netty.util.IllegalReferenceCountException: refCnt: 0, decrement: 1
at io.netty.util.internal.ReferenceCountUpdater.toLiveRealRefCnt(ReferenceCountUpdater.java:83)
at io.netty.util.internal.ReferenceCountUpdater.release(ReferenceCountUpdater.java:148)
at io.netty.buffer.AbstractReferenceCountedByteBuf.release(AbstractReferenceCountedByteBuf.java:101)
at io.netty.handler.codec.ByteToMessageDecoder.handlerRemoved(ByteToMessageDecoder.java:269)
at io.netty.channel.AbstractChannelHandlerContext.callHandlerRemoved(AbstractChannelHandlerContext.java:1122)
at io.netty.channel.DefaultChannelPipeline.callHandlerRemoved0(DefaultChannelPipeline.java:637)

</details>

Work Around: reduce number of http2 connections used by Gatling (use )

##### issue 2
With normal (no work steal mode), for the Gatling/Post test, we can observe this exception in the frontend:

<details>
  <summary>IllegalReferenceCountException: refCnt: 0, increment: 1</summary>
13:05:10.177 [reactor-http-epoll-3] WARN  r.n.http.client.HttpClientConnect - [726d00f4/154549-1, L:/10.128.15.214:35380 - R:10.128.15.218/10.128.15.218:8080] The connection observed an error
io.netty.util.IllegalReferenceCountException: refCnt: 0, increment: 1
        at io.netty.util.internal.ReferenceCountUpdater.retain0(ReferenceCountUpdater.java:133)
        at io.netty.util.internal.ReferenceCountUpdater.retain(ReferenceCountUpdater.java:120)
        at io.netty.buffer.AbstractReferenceCountedByteBuf.retain(AbstractReferenceCountedByteBuf.java:81)
        at io.netty.buffer.AbstractDerivedByteBuf.retain0(AbstractDerivedByteBuf.java:58)
        at io.netty.buffer.AbstractDerivedByteBuf.retain(AbstractDerivedByteBuf.java:54)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onNext(FluxPeek.java:185)
        at reactor.core.publisher.FluxMap$MapSubscriber.onNext(FluxMap.java:122)
        at reactor.netty.channel.FluxReceive.drainReceiver(FluxReceive.java:294)
        at reactor.netty.channel.FluxReceive.request(FluxReceive.java:133)
        at reactor.core.publisher.FluxMap$MapSubscriber.request(FluxMap.java:164)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.request(FluxPeek.java:138)
        at reactor.core.publisher.Operators$BaseFluxToMonoOperator.request(Operators.java:2041)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
        at reactor.core.publisher.FluxHandleFuseable$HandleFuseableSubscriber.request(FluxHandleFuseable.java:260)
        at reactor.core.publisher.FluxDoFinally$DoFinallySubscriber.request(FluxDoFinally.java:140)
        at reactor.core.publisher.FluxMap$MapSubscriber.request(FluxMap.java:164)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.request(FluxPeek.java:138)
        at reactor.core.publisher.FluxMap$MapSubscriber.request(FluxMap.java:164)
        at reactor.core.publisher.MonoFlatMap$FlatMapMain.request(MonoFlatMap.java:194)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.request(FluxContextWrite.java:136)
        at reactor.netty.channel.ChannelOperations.onSubscribe(ChannelOperations.java:273)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onSubscribe(FluxContextWrite.java:101)
        at reactor.core.publisher.MonoFlatMap$FlatMapMain.onSubscribe(MonoFlatMap.java:117)
        at reactor.core.publisher.FluxMap$MapSubscriber.onSubscribe(FluxMap.java:92)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onSubscribe(FluxPeek.java:171)
        at reactor.core.publisher.FluxMap$MapSubscriber.onSubscribe(FluxMap.java:92)
        at reactor.core.publisher.FluxDoFinally$DoFinallySubscriber.onSubscribe(FluxDoFinally.java:107)
        at reactor.core.publisher.FluxHandleFuseable$HandleFuseableSubscriber.onSubscribe(FluxHandleFuseable.java:164)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onSubscribe(FluxContextWrite.java:101)
        at reactor.core.publisher.Operators$BaseFluxToMonoOperator.onSubscribe(Operators.java:2025)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onSubscribe(FluxPeek.java:171)
        at reactor.core.publisher.FluxMap$MapSubscriber.onSubscribe(FluxMap.java:92)
        at reactor.netty.channel.FluxReceive.startReceiver(FluxReceive.java:172)
        at reactor.netty.channel.FluxReceive.lambda$subscribe$2(FluxReceive.java:150)
        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleT^C
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onSubscribe(FluxPeek.java:171)
        at reactor.core.publisher.FluxMap$MapSubscriber.onSubscribe(FluxMap.java:92)
        at reactor.core.publisher.FluxDoFinally$DoFinallySubscriber.onSubscribe(FluxDoFinally.java:107)
        at reactor.core.publisher.FluxHandleFuseable$HandleFuseableSubscriber.onSubscribe(FluxHandleFuseable.java:164)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onSubscribe(FluxContextWrite.java:101)
        at reactor.core.publisher.Operators$BaseFluxToMonoOperator.onSubscribe(Operators.java:2025)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onSubscribe(FluxPeek.java:171)
        at reactor.core.publisher.FluxMap$MapSubscriber.onSubscribe(FluxMap.java:92)
        at reactor.netty.channel.FluxReceive.startReceiver(FluxReceive.java:172)
        at reactor.netty.channel.FluxReceive.lambda$subscribe$2(FluxReceive.java:150)
        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
        at io.netty.channel.epoll.EpollEventLoop.run(EpollEventLoop.java:413)
        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
        at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
        at java.base/java.lang.Thread.run(Thread.java:1583)
</details>

##### issue 3

with work steal mode, we can see this exception sometimes in the frontend:
<details>
  <summary>reactor.core.publisher.Operators - Error while discarding element from a Collection, continuing with next element</summary>
13:15:46.093 [reactor-http-epoll-11] WARN  reactor.core.publisher.Operators - Error while discarding element from a Collection, continuing with next element
io.netty.util.IllegalReferenceCountException: refCnt: 0, decrement: 1
        at io.netty.util.internal.ReferenceCountUpdater.toLiveRealRefCnt(ReferenceCountUpdater.java:83)
        at io.netty.util.internal.ReferenceCountUpdater.release(ReferenceCountUpdater.java:148)
        at io.netty.buffer.AbstractReferenceCountedByteBuf.release(AbstractReferenceCountedByteBuf.java:101)
        at io.netty.buffer.AbstractDerivedByteBuf.release0(AbstractDerivedByteBuf.java:98)
        at io.netty.buffer.AbstractDerivedByteBuf.release(AbstractDerivedByteBuf.java:94)
        at reactor.core.publisher.Operators.lambda$discardLocalAdapter$0(Operators.java:389)
        at java.base/java.util.function.Consumer.lambda$andThen$0(Consumer.java:65)
        at java.base/java.util.function.Consumer.lambda$andThen$0(Consumer.java:65)
        at reactor.core.publisher.Operators.onDiscardMultiple(Operators.java:570)
        at reactor.core.publisher.MonoCollectList$MonoCollectListSubscriber.onError(MonoCollectList.java:106)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onError(FluxPeek.java:222)
        at reactor.core.publisher.FluxMap$MapSubscriber.onError(FluxMap.java:134)
        at reactor.netty.channel.FluxReceive.onInboundError(FluxReceive.java:465)
        at reactor.netty.channel.ChannelOperations.onInboundError(ChannelOperations.java:515)
        at reactor.netty.http.server.HttpServerOperations.onInboundClose(HttpServerOperations.java:699)
        at reactor.netty.channel.ChannelOperationsHandler.channelInactive(ChannelOperationsHandler.java:73)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:305)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:281)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelInactive(AbstractChannelHandlerContext.java:274)
        at reactor.netty.http.server.AbstractHttpServerMetricsHandler.channelInactive(AbstractHttpServerMetricsHandler.java:126)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:303)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:281)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelInactive(AbstractChannelHandlerContext.java:274)
        at io.netty.channel.DefaultChannelPipeline$HeadContext.channelInactive(DefaultChannelPipeline.java:1405)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:301)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelInactive(AbstractChannelHandlerContext.java:281)
        at io.netty.channel.DefaultChannelPipeline.fireChannelInactive(DefaultChannelPipeline.java:901)
        at io.netty.handler.codec.http2.AbstractHttp2StreamChannel$Http2ChannelUnsafe$2.run(AbstractHttp2StreamChannel.java:791)
        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
        at io.netty.channel.epoll.EpollEventLoop.run(EpollEventLoop.java:416)
        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
        at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
        at java.base/java.lang.Thread.run(Thread.java:1583)
</details>

##### issue 4

with work steal mode, for the Gatling/Post2 scenarion, we can see this exception in the frontend:
<details>
  <summary>io.netty.util.IllegalReferenceCountException: refCnt: 0, decrement: 1</summary>
io.netty.util.IllegalReferenceCountException: refCnt: 0, decrement: 1
        at io.netty.util.internal.ReferenceCountUpdater.toLiveRealRefCnt(ReferenceCountUpdater.java:83)
        at io.netty.util.internal.ReferenceCountUpdater.release(ReferenceCountUpdater.java:148)
        at io.netty.buffer.AbstractReferenceCountedByteBuf.release(AbstractReferenceCountedByteBuf.java:101)
        at io.netty.buffer.AbstractDerivedByteBuf.release0(AbstractDerivedByteBuf.java:98)
        at io.netty.buffer.AbstractDerivedByteBuf.release(AbstractDerivedByteBuf.java:94)
        at io.netty.util.ReferenceCountUtil.release(ReferenceCountUtil.java:90)
        at io.netty.util.ReferenceCountUtil.safeRelease(ReferenceCountUtil.java:116)
        at io.netty.buffer.CompositeByteBuf.addComponents(CompositeByteBuf.java:555)
        at io.netty.buffer.CompositeByteBuf.addComponents(CompositeByteBuf.java:251)
        at reactor.netty.ByteBufFlux.lambda$aggregate$7(ByteBufFlux.java:283)
        at reactor.core.publisher.FluxHandleFuseable$HandleFuseableSubscriber.onNext(FluxHandleFuseable.java:179)
        at reactor.core.publisher.FluxContextWrite$ContextWriteSubscriber.onNext(FluxContextWrite.java:107)
        at reactor.core.publisher.Operators$BaseFluxToMonoOperator.completePossiblyEmpty(Operators.java:2071)
        at reactor.core.publisher.MonoCollectList$MonoCollectListSubscriber.onComplete(MonoCollectList.java:118)
        at reactor.core.publisher.FluxPeek$PeekSubscriber.onComplete(FluxPeek.java:260)
        at reactor.core.publisher.FluxMap$MapSubscriber.onComplete(FluxMap.java:144)
        at reactor.core.publisher.MonoFlatMapMany$FlatMapManyInner.onComplete(MonoFlatMapMany.java:260)
        at reactor.netty.channel.FluxReceive.onInboundComplete(FluxReceive.java:415)
        at reactor.netty.channel.ChannelOperations.onInboundComplete(ChannelOperations.java:446)
        at reactor.netty.channel.ChannelOperations.terminate(ChannelOperations.java:500)
        at reactor.netty.http.client.HttpClientOperations.onInboundNext(HttpClientOperations.java:772)
        at reactor.netty.channel.ChannelOperationsHandler.channelRead(ChannelOperationsHandler.java:114)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at reactor.netty.http.client.AbstractHttpClientMetricsHandler.channelRead(AbstractHttpClientMetricsHandler.java:162)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.handler.codec.MessageToMessageDecoder.channelRead(MessageToMessageDecoder.java:103)
        at io.netty.handler.codec.MessageToMessageCodec.channelRead(MessageToMessageCodec.java:111)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
        at io.netty.handler.codec.http2.AbstractHttp2StreamChannel$Http2ChannelUnsafe.doRead0(AbstractHttp2StreamChannel.java:955)
        at io.netty.handler.codec.http2.AbstractHttp2StreamChannel.fireChildRead(AbstractHttp2StreamChannel.java:600)
        at io.netty.handler.codec.http2.Http2MultiplexHandler.channelRead(Http2MultiplexHandler.java:195)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.handler.codec.http2.Http2FrameCodec.onHttp2Frame(Http2FrameCodec.java:712)
        at io.netty.handler.codec.http2.Http2FrameCodec$FrameListener.onDataRead(Http2FrameCodec.java:651)
        at io.netty.handler.codec.http2.Http2FrameListenerDecorator.onDataRead(Http2FrameListenerDecorator.java:36)
        at io.netty.handler.codec.http2.Http2EmptyDataFrameListener.onDataRead(Http2EmptyDataFrameListener.java:49)
        at io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder$FrameReadListener.onDataRead(DefaultHttp2ConnectionDecoder.java:322)
        at io.netty.handler.codec.http2.DefaultHttp2FrameReader.readDataFrame(DefaultHttp2FrameReader.java:415)
        at io.netty.handler.codec.http2.DefaultHttp2FrameReader.processPayloadState(DefaultHttp2FrameReader.java:250)
        at io.netty.handler.codec.http2.DefaultHttp2FrameReader.readFrame(DefaultHttp2FrameReader.java:159)
        at io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder.decodeFrame(DefaultHttp2ConnectionDecoder.java:188)
        at io.netty.handler.codec.http2.DecoratingHttp2ConnectionDecoder.decodeFrame(DecoratingHttp2ConnectionDecoder.java:63)
        at io.netty.handler.codec.http2.Http2ConnectionHandler$FrameDecoder.decode(Http2ConnectionHandler.java:393)
        at io.netty.handler.codec.http2.Http2ConnectionHandler.decode(Http2ConnectionHandler.java:453)
        at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:529)
        at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:468)
        at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:290)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.handler.flush.FlushConsolidationHandler.channelRead(FlushConsolidationHandler.java:152)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at reactor.netty.channel.AbstractChannelMetricsHandler.channelRead(AbstractChannelMetricsHandler.java:132)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:442)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.handler.ssl.SslHandler.unwrap(SslHandler.java:1475)
        at io.netty.handler.ssl.SslHandler.decodeJdkCompatible(SslHandler.java:1338)
        at io.netty.handler.ssl.SslHandler.decode(SslHandler.java:1387)
        at io.netty.handler.codec.ByteToMessageDecoder.decodeRemovalReentryProtection(ByteToMessageDecoder.java:529)
        at io.netty.handler.codec.ByteToMessageDecoder.callDecode(ByteToMessageDecoder.java:468)
        at io.netty.handler.codec.ByteToMessageDecoder.channelRead(ByteToMessageDecoder.java:290)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:444)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.AbstractChannelHandlerContext.fireChannelRead(AbstractChannelHandlerContext.java:412)
        at io.netty.channel.DefaultChannelPipeline$HeadContext.channelRead(DefaultChannelPipeline.java:1410)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:440)
        at io.netty.channel.AbstractChannelHandlerContext.invokeChannelRead(AbstractChannelHandlerContext.java:420)
        at io.netty.channel.DefaultChannelPipeline.fireChannelRead(DefaultChannelPipeline.java:919)
        at io.netty.channel.epoll.AbstractEpollStreamChannel$EpollStreamUnsafe.epollInReady(AbstractEpollStreamChannel.java:800)
        at io.netty.channel.epoll.AbstractEpollChannel$AbstractEpollUnsafe$1.run(AbstractEpollChannel.java:425)
        at io.netty.util.concurrent.AbstractEventExecutor.runTask(AbstractEventExecutor.java:173)
        at io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:166)
        at io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:470)
        at io.netty.channel.epoll.EpollEventLoop.run(EpollEventLoop.java:413)
        at io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:997)
        at io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
        at io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
        at java.base/java.lang.Thread.run(Thread.java:1583)
</details>
