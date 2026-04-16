package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

@Service
@RequiredArgsConstructor
public class GammaApiClient {

    private static final String MARKET_ENDPOINT = "/markets/{marketId}";

    private final WebClient gammaApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    @Cacheable(cacheNames = "markets", key = "#marketId")
    public MarketDetailsDto getMarket(String marketId) {
        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }

        final String endpoint = MARKET_ENDPOINT.replace("{marketId}", marketId);

        return polymarketRetryTemplate.execute(context ->
                gammaApiWebClient.get()
                        .uri(MARKET_ENDPOINT, marketId)
                        .retrieve()
                        .onStatus(this::isRetryableStatus,
                                response -> response.createException().map(ex -> (Throwable) ex))
                        .onStatus(HttpStatusCode::isError,
                                response -> response.bodyToMono(String.class)
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
                        .block()
        );
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}