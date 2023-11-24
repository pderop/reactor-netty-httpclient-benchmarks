package org.example;

import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static reactor.netty.Metrics.*;

public class BenchmarkProvider {

    private final AtomicInteger currentProcessed;
    private final AtomicInteger currentErrors;
    private final AtomicInteger previewsProcessed;
    private final ConcurrentLinkedQueue<Integer> averages = new ConcurrentLinkedQueue<>();
    private final MeterRegistry registry;
    private final AtomicInteger threadsPerSecond = new AtomicInteger();
    private final AtomicReference<Thread> lastThreadSeen = new AtomicReference<>();
    private static final String CLIENT_RESPONSE_TIME = HTTP_CLIENT_PREFIX + RESPONSE_TIME;
    private static String POOL_NAME = "http";

    public BenchmarkProvider() {
        registry = new SimpleMeterRegistry();
        Metrics.addRegistry(registry);

        currentProcessed = new AtomicInteger(0);
        currentErrors = new AtomicInteger(0);
        previewsProcessed = new AtomicInteger(0);
    }

    private long getTimer(String name, String... tags) {
        Timer timer = registry.find(name).tags(tags).timer();
        if (timer != null) {
            return timer.count();
        } else {
            return -1;
        }
    }

    private double getGaugeValue(String gaugeName, String poolName) {
        Gauge gauge = registry.find(gaugeName).tag(NAME, poolName).gauge();
        double result = -1;
        if (gauge != null) {
            result = gauge.value();
        }
        return result;
    }

    public BenchmarkProvider start() {
        Flux.interval(Duration.ofSeconds(1))
                .subscribeOn(Schedulers.newSingle("banchmark"))
                .map(aLong -> calculateEventSendPerSecond())
                //.filter(integer -> integer > 0)
                .onErrorContinue((throwable, o) -> onError(throwable))
                .retry()
                .subscribe(this::printBenchmark);
        return this;
    }

    private void printBenchmark(Integer eventPerSecond) {
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        memoryUsed = memoryUsed / 1000000;
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(
                OperatingSystemMXBean.class);

        int errPerSecond = currentErrors.getAndSet(0);

        System.out.printf("req/sec=%-7d err/sec=%-7d mem(MB)=%-5d cpu(%%)=%-5d conc/sec=%-5.2s rspTime=%-5d actvStreams=%-5.0f pendingStreams=%-5.0f stealSteams=%-5.0f pendingCnx=%-5.0f actvCnx=%-5.0f idleCnx=%-5.0f totalCnx=%-5.0f\n",
                eventPerSecond,
                errPerSecond,
                memoryUsed,
                (int) (osBean.getProcessCpuLoad() * 100),
                threadsPerSecond.getAndSet(0),
                getTimer(CLIENT_RESPONSE_TIME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + ACTIVE_STREAMS, "http2." + POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + PENDING_STREAMS, "http2." + POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + STEAL_STREAMS, "http2." + POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + PENDING_CONNECTIONS, POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + ACTIVE_CONNECTIONS, POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + IDLE_CONNECTIONS, POOL_NAME),
                getGaugeValue(CONNECTION_PROVIDER_PREFIX + TOTAL_CONNECTIONS, POOL_NAME));
    }

    public void incrementProcessed() {
        currentProcessed.incrementAndGet();
        Thread currThread = Thread.currentThread();
        if (lastThreadSeen.getAndSet(currThread) != currThread) {
            threadsPerSecond.incrementAndGet();
        }
    }

    public void incrementErrors() {
        currentErrors.incrementAndGet();
        Thread currThread = Thread.currentThread();
        if (lastThreadSeen.getAndSet(currThread) != currThread) {
            threadsPerSecond.incrementAndGet();
        }
    }

    public int getGlobalAverage() {
        int size = averages.size();
        if (size == 0) {
            return -1;
        }
        int sum = averages.stream().mapToInt(Integer::valueOf).sum();
        return sum / size;
    }

    private Integer calculateEventSendPerSecond() {
        int currentProcessed = this.currentProcessed.get();
        int previewsProcessed = this.previewsProcessed.get();
        int currentCalculation = currentProcessed - previewsProcessed;
        this.previewsProcessed.set(currentProcessed);
        averages.add(currentCalculation);
        return currentCalculation;
    }

    private void onError(Throwable throwable) {
        throwable.printStackTrace();
    }
}
