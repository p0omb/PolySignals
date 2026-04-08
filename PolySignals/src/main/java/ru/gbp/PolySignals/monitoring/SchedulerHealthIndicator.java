package ru.gbp.PolySignals.monitoring;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component("schedulers")
public class SchedulerHealthIndicator implements HealthIndicator {

    private static final Duration MAX_AGE = Duration.ofHours(1);

    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();

    public void updateLastRun() {
        lastRunAt.set(Instant.now());
    }

    @Override
    public Health health() {
        Instant lastRun = lastRunAt.get();
        if (lastRun == null) {
            return Health.down()
                    .withDetail("lastRunAt", "never")
                    .withDetail("maxAge", MAX_AGE.toString())
                    .build();
        }

        Duration age = Duration.between(lastRun, Instant.now());
        if (age.compareTo(MAX_AGE) > 0) {
            return Health.down()
                    .withDetail("lastRunAt", lastRun.toString())
                    .withDetail("ageSeconds", age.getSeconds())
                    .withDetail("maxAge", MAX_AGE.toString())
                    .build();
        }

        return Health.up()
                .withDetail("lastRunAt", lastRun.toString())
                .withDetail("ageSeconds", age.getSeconds())
                .withDetail("maxAge", MAX_AGE.toString())
                .build();
    }
}

