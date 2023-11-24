package org.example;

import io.netty.buffer.Unpooled;
import org.reactivestreams.Publisher;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.http.server.HttpServerRoutes;

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Consumer;

final class RouterFunctionConfig {
    static final byte[] HELLO = "Hello".getBytes(StandardCharsets.UTF_8);
    static final byte[] ACCESS_GRANDED = "Access Granted".getBytes(StandardCharsets.UTF_8);

    static Consumer<? super HttpServerRoutes> routesBuilder() {
        return r -> r
                .get("/get", get())
                .get("/checkPriviledge", checkPriviledge())
                .post("/post", post())
                ;
    }

    static BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> get() {
        return (req, res) ->
                res.header("Content-Type", "text/plain")
                        .sendObject(Unpooled.wrappedBuffer(HELLO));
    }

    static BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> checkPriviledge() {
        return (req, res) ->
                res.header("Content-Type", "text/plain")
                        .sendObject(Unpooled.wrappedBuffer(ACCESS_GRANDED));
    }

    static BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> post() {
        return (req, res) ->
                res.header("Content-Type", "application/json")
                        .send(req.receive().retain());
    }
}