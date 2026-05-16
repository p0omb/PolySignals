package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ru.gbp.PolySignals.client.polymarket.dto.HolderDto;
import ru.gbp.PolySignals.client.polymarket.dto.MarketHoldersResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Клиент для работы с эндпоинтом /holders Polymarket Data API.
 * Получает информацию о топ-холдерах для каждого исхода маркета.
 * <p>
 * Используется для фильтрации сигналов на основе наличия крупных позиций противоположного исхода.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldersApiClient {

    private static final String HOLDERS_ENDPOINT = "/holders";

    private final WebClient dataApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    /**
     * Получить холдеров для маркета.
     *
     * @param marketId ID маркета (conditionId)
     * @param limit    максимальное количество холдеров для возврата (по умолчанию 20)
     * @return список {@link MarketHoldersResponseDto} с информацией о холдерах для каждого исхода
     */
    @Cacheable(cacheNames = "marketHolders", key = "#marketId + ':' + #limit")
    public List<MarketHoldersResponseDto> getMarketHolders(String marketId, int limit) {
        log.info("HoldersApiClient.getMarketHolders called for marketId={}, limit={}", marketId, limit);

        if (marketId == null || marketId.isBlank()) {
            log.warn("HoldersApiClient.getMarketHolders: invalid marketId (null or blank)");
            throw new IllegalArgumentException("marketId must not be blank");
        }

        if (limit <= 0) {
            log.warn("HoldersApiClient.getMarketHolders: invalid limit (must be positive): {}", limit);
            throw new IllegalArgumentException("limit must be positive");
        }

        return polymarketRetryTemplate.execute(context -> {
            List<MarketHoldersResponseDto> holders = dataApiWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(HOLDERS_ENDPOINT)
                            .queryParam("market", marketId)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .onStatus(this::isRetryableStatus, response -> response.createException().map(ex -> (Throwable) ex))
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> PolymarketApiErrorHandler.buildException(
                                    response.statusCode().value(),
                                    body,
                                    HOLDERS_ENDPOINT + "?market=" + marketId,
                                    response.headers().asHttpHeaders()
                            )))
                    .bodyToMono(new ParameterizedTypeReference<List<MarketHoldersResponseDto>>() {
                    })
                    .doOnError(e -> log.error("Holders API error for marketId={}: {}", marketId, e.getMessage(), e))
                    .block();

            if (holders == null) {
                log.warn("Holders API returned null response for marketId={}", marketId);
                return List.of();
            }

            log.info("Holders API success for marketId={}, returned {} token groups", marketId, holders.size());
            return holders;
        });
    }

    /**
     * Вычислить суммарный объём холдеров для определённого токена (исхода) маркета.
     *
     * @param marketId ID маркета
     * @param tokenId  ID токена (исхода)
     * @param limit    максимальное количество холдеров для учёта
     * @return суммарный объём или {@code BigDecimal.ZERO}, если токен не найден
     */
    public BigDecimal calculateOppositeOutcomeVolume(String marketId, String tokenId, int limit) {
        log.debug("calculateOppositeOutcomeVolume: marketId={}, tokenId={}, limit={}", marketId, tokenId, limit);

        try {
            List<MarketHoldersResponseDto> holders = getMarketHolders(marketId, limit);

            // 🔥 НОВОЕ ЛОГИРОВАНИЕ
            log.debug("getMarketHolders returned {} token groups for marketId={}", holders.size(), marketId);
            holders.forEach(h -> {
                log.debug("  Token group: token={}, holders.size={}", h.getToken(),
                        h.getHolders() != null ? h.getHolders().size() : 0);
                if (h.getHolders() != null) {
                    h.getHolders().forEach(holder ->
                            log.debug("    Holder: amount={}, asset={}", holder.getAmount(), holder.getAsset())
                    );
                }
            });

            BigDecimal result = holders.stream()
                    .peek(h -> log.debug("Checking token: {} vs requested tokenId: {}", h.getToken(), tokenId))
                    .filter(h -> Objects.equals(h.getToken(), tokenId))
                    .peek(h -> log.debug("Token matched! Holders count: {}",
                            h.getHolders() != null ? h.getHolders().size() : 0))
                    .flatMap(h -> h.getHolders() == null ? java.util.stream.Stream.empty() : h.getHolders().stream())
                    .map(HolderDto::getAmount)
                    .peek(amount -> log.debug("Holder amount: {}", amount))
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            log.debug("Total volume calculated for tokenId={}: {}", tokenId, result);
            return result;
        } catch (Exception e) {
            log.error("Error calculating opposite outcome volume for marketId={}, tokenId={}: {}",
                    marketId, tokenId, e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}

