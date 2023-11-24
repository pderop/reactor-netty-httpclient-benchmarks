package application;

import application.simulations.SimulationBase;
import io.gatling.app.Gatling;
import io.gatling.core.config.GatlingPropertiesBuilder;
import io.gatling.javaapi.core.Simulation;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Application {

    private static String runDescription;

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Application <run description> [simulation1 simulation2 ...]");
        }

        runDescription = args[0];
        String[] simulationsArgs = args.length == 1 ? findAllSimulations().toArray(String[]::new) :
                Arrays.copyOfRange(args, 1, args.length);

        final Set<String> simulations =
                findAllSimulations()
                        .filter(simulation -> Arrays.stream(simulationsArgs).anyMatch(simulation::endsWith))
                        .collect(Collectors.toSet());

        if (simulations.isEmpty()) throw new RuntimeException("Unable to find any simulation to run");
        System.out.println("Will run simulations: " + simulations);

        simulations.forEach(Application::runGatlingSimulation);
    }

    private static Stream<String> findAllSimulations() {
        final String packageName = Application.class.getPackageName();
        System.out.printf("Finding simulations in %s package%n", packageName);

        final Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return reflections.getSubTypesOf(Simulation.class)
                .stream()
                .filter(clazz -> !Modifier.isAbstract(clazz.getModifiers())) // Filter out abstract classes
                .map(Class::getName);
    }

    private static void runGatlingSimulation(String simulationFileName) {
        System.out.printf("Starting %s simulation (DURATION=%d sec)%n", simulationFileName, SimulationBase.DURATION.toSeconds());
        final GatlingPropertiesBuilder gatlingPropertiesBuilder = new GatlingPropertiesBuilder();

        gatlingPropertiesBuilder.simulationClass(simulationFileName);
        gatlingPropertiesBuilder.runDescription(runDescription);
        gatlingPropertiesBuilder.resultsDirectory("test-reports");
        try {
            Gatling.fromMap(gatlingPropertiesBuilder.build());
        } catch (Exception exception) {
            System.err.printf("Something went wrong for simulation %s %s%n", simulationFileName, exception);
            exception.printStackTrace();
        }
    }
}
