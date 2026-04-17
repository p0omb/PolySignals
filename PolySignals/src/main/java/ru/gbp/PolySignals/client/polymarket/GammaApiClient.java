package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GammaApiClient {

    private static final String MARKETS_ENDPOINT = "/markets";

    private final WebClient gammaApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    @Cacheable(cacheNames = "markets", key = "#marketId")
    public MarketDetailsDto getMarket(String marketId) {
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }

        final String endpoint = MARKETS_ENDPOINT + "?condition_ids[]=" + marketId;

        return polymarketRetryTemplate.execute(context -> {
            List<MarketDetailsDto> markets = gammaApiWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(MARKETS_ENDPOINT)
                            .queryParam("condition_ids[]", marketId)
                            .build())
                    .retrieve()
                    .onStatus(this::isRetryableStatus, response -> response.createException().map(ex -> (Throwable) ex))
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> PolymarketApiErrorHandler.buildException(
                                    response.statusCode().value(),
                                    body,
                                    endpoint,
                                    response.headers().asHttpHeaders()
                            )))
                    .bodyToMono(new ParameterizedTypeReference<List<MarketDetailsDto>>() {})
                    .doOnError(e -> log.error("Gamma API error for marketId={}: {}", marketId, e.getMessage(), e))
                    .block();

            if (markets == null || markets.isEmpty()) {
                return null;
            }

            MarketDetailsDto dto = markets.get(0);
            if (dto.getMarketId() == null || dto.getMarketId().isBlank()) {
                dto.setMarketId(marketId);
            }
            return dto;
        });
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}