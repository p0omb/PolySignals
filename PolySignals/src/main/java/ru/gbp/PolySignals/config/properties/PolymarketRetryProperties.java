package ru.gbp.PolySignals.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.polymarket.retry")
public class PolymarketRetryProperties {

    @NotNull
    @Positive
    private Integer maxAttempts;

    @NotNull
    private Duration initialDelay;

    @NotNull
    @Positive
    private Double multiplier;

    @Positive
    public long getInitialDelayMillis() {
        return initialDelay == null ? 0 : initialDelay.toMillis();
    }
}

