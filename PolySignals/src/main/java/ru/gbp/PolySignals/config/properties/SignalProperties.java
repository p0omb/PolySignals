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
@ConfigurationProperties(prefix = "app.signal")
public class SignalProperties {

    @NotNull
    private Duration cooldown;

    @NotNull
    private Duration window;

    @NotNull
    private Duration backupPeriod;

    @Positive
    public long getCooldownSeconds() {
        return cooldown == null ? 0 : cooldown.getSeconds();
    }

    @Positive
    public long getWindowSeconds() {
        return window == null ? 0 : window.getSeconds();
    }

    @Positive
    public long getBackupPeriodSeconds() {
        return backupPeriod == null ? 0 : backupPeriod.getSeconds();
    }
}

