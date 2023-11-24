package application.simulations;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.gatling.javaapi.http.HttpDsl.http;

public class Get extends SimulationBase {
    {
        HttpProtocolBuilder httpProtocolBuilder = http.baseUrl("https://" + FRONTEND_HOST + ":" + FRONTEND_PORT)
                .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                .acceptLanguageHeader("en-US,en;q=0.5")
                .acceptEncodingHeader("gzip, deflate")
                .userAgentHeader("Gatling");

        HttpRequestActionBuilder requestBuilder = http("get")
                .get("/get");

        httpProtocolBuilder = httpProtocolBuilder.enableHttp2();
        requestBuilder = requestBuilder.resources(IntStream.range(0, H2_CONCURRENCY)
                .mapToObj(i -> http("req" + (i + 1))
                        .get("/get"))
                .collect(Collectors.toList()));

        setUp(httpProtocolBuilder, requestBuilder, "Get Scenario");
    }
}
