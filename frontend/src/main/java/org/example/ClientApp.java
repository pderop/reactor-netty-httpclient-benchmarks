package org.example;

import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.stream.Collectors;

public class ClientApp {

    final static int LOOPS = Integer.getInteger("requests", 10000000);
    final static Logger log = LoggerFactory.getLogger(ClientApp.class);
    final static String SCENARIO = System.getProperty("scenario", "get");  // "get", "post", or "post2"

    final String JSON;
    final byte[] JSON_BYTES;

    static class BenchmarkHolder {
        final static BenchmarkProvider benchmarkProvider = new BenchmarkProvider().start();
    }

    public static void main(String[] args) throws IOException {
        ClientApp app = new ClientApp();
        app.run();
    }

    ClientApp() throws IOException {
        try (InputStream inputStream = ClientApp.class.getResourceAsStream("/user.json")) {
            JSON = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))
                    .lines()
                    .collect(Collectors.joining("\n"));

            JSON_BYTES = JSON.getBytes(Charset.defaultCharset());
        }
    }

    public void run() {
        System.out.println("Warmup ...");
        sendRequests(LOOPS, false, 5)
                .block();
        System.out.println("Running ...");
        long duration = Long.getLong("duration", 10);
        sendRequests(LOOPS, true, duration)
                .block();
        System.out.println("Average rsp/sec: " + BenchmarkHolder.benchmarkProvider.getGlobalAverage());
        System.exit(0);
    }

    Mono<String> sendRequests(int count, boolean doStatistics, long duration) {
        Sinks.One<String> done = Sinks.one();

        Flux.range(1, count)
                .flatMap(integer -> send(doStatistics), 1000)
                .takeUntilOther(Mono.delay(Duration.ofSeconds(duration)).subscribeOn(Schedulers.parallel()))
                .subscribe(
                        s -> {
                        },
                        err -> done.tryEmitValue("Error: " + err.toString()),
                        () -> done.tryEmitValue("done."));

        return done.asMono();
    }

    Publisher<String> send(boolean doStatistics) {
        switch (SCENARIO) {
            case "get":
                return get(doStatistics);
            case "post":
                return post(doStatistics);
            default:
                throw new IllegalStateException("wrong scenario: " + SCENARIO);
        }
    }

    Publisher<String> get(boolean doStatistics) {
        return Client.CLIENT
                .get()
                .uri("/get")
                .responseContent()
                .aggregate()
                .asString()
                .doOnNext(s -> {
                    if (doStatistics) {
                        BenchmarkHolder.benchmarkProvider.incrementProcessed();
                    }
                })
                .onErrorResume(e -> {
                    log.warn(e.toString());
                    if (doStatistics) {
                        BenchmarkHolder.benchmarkProvider.incrementErrors();
                    }
                    return Mono.just(e.toString());
                });
    }

    Publisher<String> post(boolean doStatistics) {
        return Client.CLIENT
                .headers(headers -> headers.set("Content-Type", "application/json"))
                .post()
                .uri("/post")
                .send((req, out) -> out.sendObject(Unpooled.wrappedBuffer(JSON_BYTES)))
                .responseContent()
                .aggregate()
                .asString()
                .doOnNext(s -> {
                    if (doStatistics) {
                        BenchmarkHolder.benchmarkProvider.incrementProcessed();
                    }
                })
                .onErrorResume(e -> {
                    if (doStatistics) {
                        BenchmarkHolder.benchmarkProvider.incrementErrors();
                    }
                    return Mono.just(e.toString());
                });
    }
}
