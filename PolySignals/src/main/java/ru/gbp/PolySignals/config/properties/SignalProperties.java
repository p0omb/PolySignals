package ru.gbp.PolySignals.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.PositiveOrZero;
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

    @PositiveOrZero
    private BigDecimal minTotalAmount = BigDecimal.ZERO;

    /**
     * Включить фильтрацию сигналов на основе топ-холдеров противоположного исхода.
     * Если true, перед сохранением сигнала проверяется объём позиций противоположного исхода.
     * Если объём превышает {@link #oppositeHoldersMinAmount}, сигнал отбрасывается.
     */
    private Boolean filterOppositeHolders = false;

    /**
     * Минимальный суммарный объём топ-холдеров противоположного исхода для отклонения сигнала.
     * Если объём холдеров >= этого значения, сигнал не будет сохранён.
     * Default: 0 (фильтрация отключена).
     */
    @PositiveOrZero
    private BigDecimal oppositeHoldersMinAmount = BigDecimal.ZERO;

    /**
     * Количество топ-холдеров для учёта при анализе противоположного исхода.
     * Default: 20.
     */
    @Positive
    private Integer oppositeHoldersLimit = 20;

    private List<String> excludedTitlePatterns = List.of();
}

