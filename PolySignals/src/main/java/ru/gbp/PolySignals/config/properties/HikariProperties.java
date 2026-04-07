package ru.gbp.PolySignals.config.properties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "spring.datasource.hikari")
public class HikariProperties {

    @NotNull
    @Positive
    private Integer maximumPoolSize;

    @NotNull
    @Positive
    private Integer minimumIdle;

    @NotNull
    @Positive
    private Long connectionTimeout;
}

