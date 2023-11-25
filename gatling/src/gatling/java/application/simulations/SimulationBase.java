package application.simulations;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.time.Duration;
import java.util.regex.Pattern;

import static io.gatling.javaapi.core.CoreDsl.*;

public abstract class SimulationBase extends Simulation {

    public static final String FRONTEND_HOST = System.getProperty("frontend.host", "127.0.0.1");
    public static final int FRONTEND_PORT = Integer.parseInt(System.getProperty("frontend.port", "8090"));
    public static final Duration DURATION = parseDuration(System.getProperty("duration", "2m"));
    public static final int INCREMENT = Integer.getInteger("increment", 1);
    public static final int STEPS = Integer.getInteger("steps", Runtime.getRuntime().availableProcessors());
    public static final Duration LEVEL_LASTING = parseDuration(System.getProperty("level.lasting", "2"));
    public static final Duration RAMP_LASTING = parseDuration(System.getProperty("ramp.lasting", "1"));
    public static final int H2_CONCURRENCY = Integer.getInteger("h2.concurrency", 100);

    protected void setUp(HttpProtocolBuilder httpProtocolBuilder, HttpRequestActionBuilder requestBuilder, String scnName) {
        ScenarioBuilder scn = scenario(scnName)
                .forever().on(exec(requestBuilder));

        setUp(scn
                .injectClosed(
                        incrementConcurrentUsers(INCREMENT)
                                .times(STEPS)
                                .eachLevelLasting(LEVEL_LASTING)
                                .separatedByRampsLasting(RAMP_LASTING)
                                .startingFrom(0)))
                .maxDuration(DURATION)
                .protocols(httpProtocolBuilder);
    }

    public static Duration parseDuration(String durationProperty) {
        boolean endsWithSMH = Pattern.compile("(m|s|h)$", Pattern.CASE_INSENSITIVE).matcher(durationProperty).find();
        if (endsWithSMH) {
            String unit = durationProperty.substring(durationProperty.length() - 1);
            String value = durationProperty.substring(0, durationProperty.length() - 1);

            try {
                long amount = Long.parseLong(value);
                switch (unit.toLowerCase()) {
                    case "s":
                        return Duration.ofSeconds(amount);
                    case "m":
                        return Duration.ofMinutes(amount);
                    case "h":
                        return Duration.ofHours(amount);
                    default:
                        // Default: Duration in minutes
                        return Duration.ofSeconds(amount);
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Duration.ofSeconds(Long.parseLong(durationProperty));
        }
    }
}
