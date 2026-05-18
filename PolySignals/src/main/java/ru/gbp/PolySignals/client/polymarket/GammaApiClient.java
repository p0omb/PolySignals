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

        log.info("Getting market details from Data API for condition_id={}", marketId);

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
                // 1. Запрос к Data API
                List<MarketDetailsDto> markets = gammaApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(MARKETS_ENDPOINT)
                                .queryParam("condition_ids", requestedId) // <-- используем condition_ids без []
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

                // 2. Обработка ответа
                if (markets == null || markets.isEmpty()) {
                    log.warn("Data API returned empty or null response for condition_id={}", requestedId);
                    return null;
                }

                if (markets.size() > 1) {
                    log.warn("Data API returned {} markets for condition_id={}, using the first one.", markets.size(), requestedId);
                }

                MarketDetailsDto market = markets.get(0);
                if (market.getClobTokenIds().isEmpty()) {
                    log.warn("Market found in Data API, but clob_token_ids is empty for condition_id={}", requestedId);
                }

                // 3. Кешируем и возвращаем результат
                marketCache.put(cacheKey, market);
                log.info("Successfully fetched market from Data API for condition_id={}. Tokens: {}", requestedId, market.getClobTokenIds());
                return market;

            }, context -> {
                log.error("Data API failed for condition_id={} after retries, returning null", requestedId);
                return null;
            });

            return result;
        } catch (Exception ex) {
            log.error("Data API unexpected error for condition_id={}: {}", requestedId, ex.getMessage(), ex);
            return null;
        }
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}