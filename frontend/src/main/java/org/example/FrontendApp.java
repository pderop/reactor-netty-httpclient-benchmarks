package org.example;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import reactor.netty.http.Http2SslContextSpec;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.server.HttpServer;

import java.security.cert.CertificateException;
import java.util.function.Function;

public class FrontendApp {
    static final String FRONTEND_HOST = System.getProperty("frontend.host", "0");
    static final int FRONTEND_PORT = Integer.getInteger("frontend.port", 8090);

    static HttpServer configure(HttpServer server) {
        try {
            SelfSignedCertificate cert = new SelfSignedCertificate();
            return server
                    .secure(spec -> spec.sslContext(Http2SslContextSpec.forServer(cert.certificate(), cert.privateKey())))
                    .protocol(HttpProtocol.HTTP11, HttpProtocol.H2);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (Client.NO_HTTPCLIENT_COLOC) {
            System.out.println("Disable HttpClient Colocation");
        }

        if (Client.ENABLE_WORKSTEALING) {
            System.out.println("Enabling Http2Client Work Stealing");
        }

        configure(HttpServer.create()
                .host(FRONTEND_HOST)
                .port(FRONTEND_PORT))
                .metrics(true, Function.identity())
                .route(RouterFunctionConfig.routesBuilder())
                .doOnBound(server -> System.out.println("Server is bound on " + server.address()))
                .bindNow()
                .onDispose()
                .block();
    }
}
