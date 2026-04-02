package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

@Service
@RequiredArgsConstructor
public class GammaApiClient {

    private static final String MARKET_ENDPOINT = "/markets/%s";

    private final WebClient gammaApiWebClient;

    @Cacheable(cacheNames = "markets", key = "#marketId")
    public MarketDetailsDto getMarket(String marketId) {
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }

        final String endpoint = MARKET_ENDPOINT.formatted(marketId);
        return gammaApiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/markets/{marketId}")
                        .build(marketId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> PolymarketApiErrorHandler.buildException(
                                response.statusCode().value(),
                                body,
                                endpoint,
                                response.headers().asHttpHeaders()
                        )))
                .bodyToMono(MarketDetailsDto.class)
                .onErrorMap(WebClientRequestException.class,
                        ex -> PolymarketApiErrorHandler.fromTransportError(ex, endpoint))
                .block();
    }
}