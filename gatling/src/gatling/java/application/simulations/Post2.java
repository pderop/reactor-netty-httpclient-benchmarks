package application.simulations;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.http.HttpDsl.http;

public class Post2 extends SimulationBase {

    static final String JSON;
    static final byte[] JSON_BYTES;

    static {
        try {
            try (InputStream inputStream = Post2.class.getResourceAsStream("/user.json")) {
                JSON = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))
                        .lines()
                        .collect(Collectors.joining("\n"));

                JSON_BYTES = JSON.getBytes(Charset.defaultCharset());
            }
        } catch (
                IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    {
        HttpProtocolBuilder httpProtocolBuilder = http.baseUrl("https://" + FRONTEND_HOST + ":" + FRONTEND_PORT)
                .acceptHeader("application/json")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Gatling");

        HttpRequestActionBuilder requestBuilder = http("post2")
                .post("/post2")
                .header("Content-Type", "application/json")
                .body(StringBody(JSON));


        httpProtocolBuilder = httpProtocolBuilder.enableHttp2();
        requestBuilder = requestBuilder.resources(IntStream.range(0, H2_CONCURRENCY)
                .mapToObj(i -> http("req" + (i + 1))
                        .post("/post2")
                        .header("Content-Type", "application/json")
                        .body(StringBody(JSON)))
                .collect(Collectors.toList()));

        setUp(httpProtocolBuilder, requestBuilder, "Post2 Scenario");
    }
}
