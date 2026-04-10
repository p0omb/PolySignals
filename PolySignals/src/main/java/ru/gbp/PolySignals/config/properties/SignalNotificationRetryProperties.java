package ru.gbp.PolySignals.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.notification.retry")
public class SignalNotificationRetryProperties {

    @Min(1)
    private int maxAttempts = 3;

    @NotNull
    private Duration initialInterval = Duration.ofSeconds(1);

    @NotNull
    private Duration maxInterval = Duration.ofSeconds(30);

    @Min(1)
    private double multiplier = 2.0;
}

