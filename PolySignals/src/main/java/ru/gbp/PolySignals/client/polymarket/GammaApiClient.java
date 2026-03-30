package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GammaApiClient {

    private final WebClient gammaApiWebClient;

    @Cacheable(cacheNames = "markets", key = "#marketId")
    public MarketDetailsDto getMarket(String marketId) {
        return gammaApiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/markets/{marketId}")
                        .build(marketId))
                .retrieve()
                .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals, this::handle429)
                .bodyToMono(MarketDetailsDto.class)
                .block();
    }

    private Mono<? extends Throwable> handle429(ClientResponse response) {
        return Mono.error(new RateLimitException("429 from Gamma API"));
    }
}