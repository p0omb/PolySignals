package ru.gbp.PolySignals.domain.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SignalMetrics {

    private final MeterRegistry meterRegistry;
    private Counter detectedSignalsCounter;

    @PostConstruct
    void init() {
        this.detectedSignalsCounter = Counter.builder("polysignals.signals.detected.total")
                .description("Total number of detected whale signals")
                .register(meterRegistry);
    }

    public void incrementDetectedSignals() {
        detectedSignalsCounter.increment();
    }
}

