package ru.gbp.PolySignals.client.polymarket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GammaApiClient {

    private static final String MARKETS_ENDPOINT = "/markets";

    private final WebClient gammaApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    @Value("${app.polymarket.gamma.market-cache-ttl:PT30M}")
    private Duration marketCacheTtl;

    // Инициализация кеша по‑умолчанию (гарантирует не‑null при прямом new GammaApiClient(...) в тестах).
    private volatile Cache<String, MarketDetailsDto> marketCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(30))
            .maximumSize(1_000)
            .build();

    // simple lock for safe lazy init
    private final Object cacheLock = new Object();

    @PostConstruct
    void initCache() {
        // При запуске в контейнере переинициализируем кеш с конфигурационным TTL (если задан).
        Duration ttl = marketCacheTtl != null ? marketCacheTtl : Duration.ofMinutes(30);
        synchronized (cacheLock) {
            this.marketCache = Caffeine.newBuilder()
                    .expireAfterWrite(ttl)
                    .maximumSize(1_000)
                    .build();
        }
        log.debug("Initialized GammaApiClient marketCache with ttl={} and maxSize={}", ttl, 1_000);
    }

    private void ensureCacheInitialized() {
        if (marketCache == null) {
            synchronized (cacheLock) {
                if (marketCache == null) {
                    Duration ttl = marketCacheTtl != null ? marketCacheTtl : Duration.ofMinutes(30);
                    this.marketCache = Caffeine.newBuilder()
                            .expireAfterWrite(ttl)
                            .maximumSize(1_000)
                            .build();
                    log.debug("Lazily initialized GammaApiClient marketCache with ttl={} and maxSize={}", ttl, 1_000);
                }
            }
        }
    }

    public MarketDetailsDto getMarket(String marketId) {
        ensureCacheInitialized();

        log.info("GammaApiClient.getMarket called for marketId={}", marketId);

        if (marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }

        String requestedId = marketId.trim();
        String cacheKey = requestedId.toLowerCase();

        // Проверяем кеш
        MarketDetailsDto cached = marketCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Data API cache hit for condition_id={}", requestedId);
            return cached;
        }

        try {
            MarketDetailsDto result = polymarketRetryTemplate.execute(context -> {
                log.debug("Executing Gamma API request for condition_id={}", requestedId);
                List<MarketDetailsDto> markets = gammaApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(MARKETS_ENDPOINT)
                                .queryParam("condition_ids", requestedId) // без скобок!
                                .build())
                        .retrieve()
                        .onStatus(this::isRetryableStatus, response ->
                                response.createException().map(ex -> (Throwable) ex))
                        .onStatus(HttpStatusCode::isError, response ->
                                response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .map(body -> PolymarketApiErrorHandler.buildException(
                                                response.statusCode().value(),
                                                body,
                                                MARKETS_ENDPOINT + "?condition_ids=" + requestedId,
                                                response.headers().asHttpHeaders()
                                        )))
                        .bodyToMono(new ParameterizedTypeReference<List<MarketDetailsDto>>() {})
                        .block();

                log.info("Gamma API response for condition_id={}: number of markets = {}",
                        requestedId, markets != null ? markets.size() : 0);

                if (markets == null || markets.isEmpty()) {
                    log.warn("Gamma API returned empty or null response for condition_id={}", requestedId);
                    return null;
                }

                // Если рынков несколько, всё равно берём первый (обычно он один)
                MarketDetailsDto market = markets.get(0);
                log.info("Using first market from response: conditionId={}, question='{}', outcomes={}, tokenIds={}",
                        market.getConditionId(),
                        market.getQuestion(),
                        market.getOutcomes(),
                        market.getClobTokenIds());

                if (market.getClobTokenIds().isEmpty()) {
                    log.warn("Market found, but clob_token_ids is empty for condition_id={}", requestedId);
                }

                // Кешируем и возвращаем
                marketCache.put(cacheKey, market);
                log.info("Successfully fetched and cached market for condition_id={}. Tokens: {}",
                        requestedId, market.getClobTokenIds());
                return market;

            }, context -> {
                log.error("Gamma API failed for condition_id={} after retries, returning null", requestedId);
                return null;
            });

            return result;
        } catch (Exception ex) {
            log.error("Gamma API unexpected error for condition_id={}: {}", requestedId, ex.getMessage(), ex);
            return null;
        }
    }
    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}