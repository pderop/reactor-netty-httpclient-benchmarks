package org.example;

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.Http2AllocationStrategy;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Client {
    static String POOL_NAME = "http";

    static final boolean ENABLE_WORKSTEALING = Boolean.getBoolean("steal");
    static final boolean NO_HTTPCLIENT_COLOC = Boolean.getBoolean("nocoloc");
    static final String BACKEND_HOST = System.getProperty("backend.host", "127.0.0.1");
    static final int BACKEND_PORT = Integer.parseInt(System.getProperty("backend.port", "8080"));
    static final int IOWORKERS = Integer.getInteger("reactor.netty.ioWorkerCount", Runtime.getRuntime().availableProcessors());
    static final int H2CLIENT_MAX_CONNECTION = Integer.getInteger("h2client.maxconn", IOWORKERS);
    static final int H2CLIENT_MAX_STREAMS = Integer.getInteger("h2client.maxstreams", 100);
    static final ConnectionProvider CONNECTION_PROVIDER = configure(ConnectionProvider.builder(POOL_NAME));
    static final HttpClient CLIENT = configure(HttpClient.create(CONNECTION_PROVIDER));

    static ConnectionProvider configure(ConnectionProvider.Builder builder) {
        builder = builder.maxConnections(500)
                //.maxIdleTime(Duration.ofMillis(100))
                .pendingAcquireMaxCount(8 * 500);

        builder = builder
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .metrics(true);

        Http2AllocationStrategy.Builder h2AllocBuilder = Http2AllocationStrategy.builder()
                .minConnections(1)
                .maxConnections(H2CLIENT_MAX_CONNECTION)
                .maxConcurrentStreams(H2CLIENT_MAX_STREAMS);

        if (ENABLE_WORKSTEALING) {
            h2AllocBuilder = h2AllocBuilder.enableWorkStealing(true);
        }

        HashedWheelTimer timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS, 1024);
        builder = builder.pendingAcquireTimer((r, d) -> {
            Timeout t = timer.newTimeout(timeout -> r.run(), d.toNanos(), TimeUnit.NANOSECONDS);
            return t::cancel;
        });

        builder = builder.allocationStrategy(h2AllocBuilder.build());
        return builder.build();
    }

    static HttpClient configure(HttpClient client) {
        client = client
                .metrics(true, Function.identity())
                .wiretap(false)
                .protocol(HttpProtocol.H2)
                .secure(spec -> spec.sslContext(Http2SslContextSpec.forClient().configure(builder -> builder.trustManager(InsecureTrustManagerFactory.INSTANCE))))
                .baseUrl("https://" + BACKEND_HOST + ":" + BACKEND_PORT);
        if (NO_HTTPCLIENT_COLOC) {
            // It is crucial to let the http client use a dedicated event loop group without colocation mode, else the httpclient
            // will most likely be single threaded during gatling load tests.
            client = client.runOn(LoopResources.create("client-loops", 1, IOWORKERS, true, false));
        }
        return client;
    }
}
