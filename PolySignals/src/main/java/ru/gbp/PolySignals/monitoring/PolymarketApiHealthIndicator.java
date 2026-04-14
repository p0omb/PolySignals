package ru.gbp.PolySignals.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component("polymarketApi")
@RequiredArgsConstructor
public class PolymarketApiHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String LEADERBOARD_ENDPOINT_TEMPLATE =
            "%s/v1/leaderboard?limit=1&offset=0&orderBy=PNL&timePeriod=DAY&category=OVERALL";

    private final WebClient.Builder webClientBuilder;

    @Value("${polymarket.data-api.base-url:https://data-api.polymarket.com}")
    private String dataApiBaseUrl;

    @Override
    public Health health() {
        final String endpoint = LEADERBOARD_ENDPOINT_TEMPLATE.formatted(dataApiBaseUrl);

        try {
            HttpStatusCode status = webClientBuilder.build()
                    .get()
                    .uri(endpoint)
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT)
                    .getStatusCode();

            if (status.is2xxSuccessful()) {
                return Health.up()
                        .withDetail("endpoint", endpoint)
                        .withDetail("status", status.value())
                        .build();
            }

            return Health.down()
                    .withDetail("endpoint", endpoint)
                    .withDetail("status", status.value())
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("endpoint", endpoint)
                    .build();
        }
    }
}
