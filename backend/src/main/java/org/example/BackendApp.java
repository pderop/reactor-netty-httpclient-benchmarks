package org.example;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.security.cert.CertificateException;
import java.util.function.Function;

public class BackendApp {
    static final String BACKEND_HOST = System.getProperty("backend.host", "0");
    static final int BACKEND_PORT = Integer.parseInt(System.getProperty("backend.port", "8080"));

    public static void main(String[] args) {
        configure(HttpServer.create()
                .host(BACKEND_HOST)
                .port(BACKEND_PORT))
                .route(RouterFunctionConfig.routesBuilder())
                .metrics(true, Function.identity())
                .doOnBound(server -> System.out.println("Server is bound on " + server.address()))
                .bindNow()
                .onDispose()
                .block();
    }

    static HttpServer configure(HttpServer server) {
        try {
            SelfSignedCertificate cert = new SelfSignedCertificate();
            return server
                    .secure(spec -> spec.sslContext(Http2SslContextSpec.forServer(cert.certificate(), cert.privateKey())))
                    .protocol(HttpProtocol.H2);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }
}
