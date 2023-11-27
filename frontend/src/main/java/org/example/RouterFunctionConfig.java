package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

import java.io.IOException;
import java.util.function.Consumer;

import static org.example.Client.CLIENT;

final class RouterFunctionConfig {
    static final Logger log = LoggerFactory.getLogger(RouterFunctionConfig.class);
    static final ObjectMapper MAPPER = new ObjectMapper();

    static class BenchmarkHolder {
        final static BenchmarkProvider benchmarkProvider = new BenchmarkProvider().start();
    }

    static Consumer<? super HttpServerRoutes> routesBuilder() {
        return routes -> routes
                .get("/get", (req, resp) -> get(req, resp))
                .post("/post", (req, resp) -> post(req, resp))
                .post("/post2", (req, resp) -> post2(req, resp));
    }

    static Publisher<Void> get(HttpServerRequest req, HttpServerResponse rsp) {
        return rsp.send(CLIENT
                .get()
                .uri("/get")
                .responseContent()
                .retain()
                .doOnNext(byteBuf -> BenchmarkHolder.benchmarkProvider.incrementProcessed()));
    }

    static Publisher<Void> post(HttpServerRequest req, HttpServerResponse rsp) {
        return rsp.send(CLIENT
                .headers(headers -> headers.set("Content-Type", "application/json"))
                .post()
                .uri("/post")
//                .send((in, out) -> out.send(req.receive().retain()))
                .send((in, out) -> out.send(req.receive().aggregate().retain()))
                .responseContent()
                .retain()
                .doOnComplete(() -> BenchmarkHolder.benchmarkProvider.incrementProcessed()));
    }

    static Publisher<Void> post2(HttpServerRequest req, HttpServerResponse rsp) {
        return req
                .receive()
                .aggregate()
                .asString()
                .flatMap(payload -> {
                    try {
                        User user = parseUser(payload);

                        return checkPrivileges(user)
                                .flatMap(accessGranted -> {
                                    if (accessGranted) {
//                                        return rsp.send(forwardToUpstream(encoreUser(user)).aggregate().retain())
//                                                .then();
                                        return rsp.sendString(forwardToUpstream(encodeUser(user)).aggregate().asString()
                                                        .doOnNext(s -> BenchmarkHolder.benchmarkProvider.incrementProcessed()))
                                                .then();
                                    } else {
                                        return rsp.status(403).send();
                                    }
                                });
                    } catch (IOException e) {
                        log.warn(e.toString());
                        return rsp.status(500).send();
                    }
                });
    }

    static Mono<Boolean> checkPrivileges(User user) {
        return CLIENT
                .get()
                .uri("/checkPriviledge")
                .responseConnection((response, connection) -> connection.inbound()
                            .receive()
                            .aggregate()
                            .asString()
                            .map(body -> response.status().code() == 200))
                .single();
    }

    static ByteBufFlux forwardToUpstream(String payload) {
        return CLIENT
                .headers(headers -> headers.set("Content-Type", "application/json"))
                .post()
                .uri("/post")
                .send((in, out) -> out.sendString(Mono.just(payload)))
                .responseContent();
    }

    static User parseUser(String json) throws IOException {
        JsonNode jsonNode = MAPPER.readTree(json);
        return MAPPER.treeToValue(jsonNode, User.class);
    }

    static String encodeUser(User user) {
        try {
            return MAPPER.writeValueAsString(user);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}