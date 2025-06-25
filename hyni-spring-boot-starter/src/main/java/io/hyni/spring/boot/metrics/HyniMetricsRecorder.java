package io.hyni.spring.boot.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Records metrics for Hyni operations
 */
@Component
public class HyniMetricsRecorder {

    private static final String METRIC_PREFIX = "hyni";

    private final MeterRegistry meterRegistry;

    public HyniMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record an API call
     */
    public void recordApiCall(String provider, String model, long duration, boolean success) {
        // Record call count
        Counter.builder(METRIC_PREFIX + ".api.calls")
            .description("Number of API calls made")
            .tag("provider", provider)
            .tag("model", model != null ? model : "unknown")
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .increment();

        // Record duration
        Timer.builder(METRIC_PREFIX + ".api.duration")
            .description("Duration of API calls")
            .tag("provider", provider)
            .tag("model", model != null ? model : "unknown")
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(duration, TimeUnit.MILLISECONDS);
    }

    /**
     * Record token usage
     */
    public void recordTokenUsage(String provider, String model, int inputTokens, int outputTokens) {
        // Input tokens
        Counter.builder(METRIC_PREFIX + ".tokens.input")
            .description("Number of input tokens used")
            .tag("provider", provider)
            .tag("model", model != null ? model : "unknown")
            .register(meterRegistry)
            .increment(inputTokens);

        // Output tokens
        Counter.builder(METRIC_PREFIX + ".tokens.output")
            .description("Number of output tokens generated")
            .tag("provider", provider)
            .tag("model", model != null ? model : "unknown")
            .register(meterRegistry)
            .increment(outputTokens);
    }

    /**
     * Record cache hit/miss
     */
    public void recordCacheMetrics(boolean hit) {
        Counter.builder(METRIC_PREFIX + ".cache.requests")
            .description("Number of cache requests")
            .tag("result", hit ? "hit" : "miss")
            .register(meterRegistry)
            .increment();
    }

    /**
     * Record error
     */
    public void recordError(String provider, String errorType) {
        Counter.builder(METRIC_PREFIX + ".errors")
            .description("Number of errors")
            .tag("provider", provider)
            .tag("type", errorType)
            .register(meterRegistry)
            .increment();
    }

    /**
     * Create a timer for measuring operations
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Stop timer and record
     */
    public void stopTimer(Timer.Sample sample, String operation, String provider) {
        sample.stop(Timer.builder(METRIC_PREFIX + ".operation.duration")
            .description("Duration of Hyni operations")
            .tag("operation", operation)
            .tag("provider", provider)
            .register(meterRegistry));
    }
}
