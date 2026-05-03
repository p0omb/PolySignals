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

        log.info("GammaApiClient.getMarket called for marketId={}", marketId);

        if (marketId == null || marketId.isBlank()) {
            log.warn("GammaApiClient.getMarket: invalid marketId (null or blank)");
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
                    .bodyToMono(new ParameterizedTypeReference<List<MarketDetailsDto>>() {
                    })
                    .doOnError(e -> log.error("Gamma API error for marketId={}: {}", marketId, e.getMessage(), e))
                    .block();

            if (markets == null || markets.isEmpty()) {
                log.warn("Gamma API returned empty or null response for marketId={}", marketId);
                return null;
            }

            MarketDetailsDto dto = markets.get(0);
            if (dto.getMarketId() == null || dto.getMarketId().isBlank()) {
                log.debug("MarketId missing in response, setting fallback marketId={}", marketId);
                dto.setMarketId(marketId);
            }
            log.info("Gamma API success for marketId={}, question={}, slug={}", marketId, dto.getQuestion(), dto.getSlug());
            return dto;

        });
    }

    /**
     * Получить список CLOB токен ID (условные ID исходов) для маркета.
     * Используется для сопоставления исхода (YES/NO) с соответствующим токеном при анализе холдеров.
     *
     * Например, для маркета YES может быть токен 0x1234..., для NO — 0x5678...
     *
     * @param marketId ID маркета (conditionId)
     * @return список CLOB токен ID, или пустой список, если маркет не найден
     */
    public List<String> getClobTokenIds(String marketId) {
        log.info("GammaApiClient.getClobTokenIds called for marketId={}", marketId);

        try {
            MarketDetailsDto market = getMarket(marketId);
            if (market != null && market.getClobTokenIds() != null && !market.getClobTokenIds().isEmpty()) {
                log.info("CLOB token IDs for marketId={}: {}", marketId, market.getClobTokenIds());
                return market.getClobTokenIds();
            }
            log.warn("No CLOB token IDs found for marketId={}", marketId);
            return List.of();
        } catch (Exception e) {
            log.error("Error getting CLOB token IDs for marketId={}: {}", marketId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Получить список исходов маркета из Gamma API.
     *
     * @param marketId ID маркета (conditionId)
     * @return список исходов или пустой список, если маркет не найден/поле отсутствует
     */
    public List<String> getOutcomes(String marketId) {
        log.info("GammaApiClient.getOutcomes called for marketId={}", marketId);

        try {
            MarketDetailsDto market = getMarket(marketId);
            if (market != null && market.getOutcomes() != null && !market.getOutcomes().isEmpty()) {
                log.info("Outcomes for marketId={}: {}", marketId, market.getOutcomes());
                return market.getOutcomes();
            }
            log.warn("No outcomes found for marketId={}", marketId);
            return List.of();
        } catch (Exception e) {
            log.error("Error getting outcomes for marketId={}: {}", marketId, e.getMessage(), e);
            return List.of();
        }
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 429 || statusCode.is5xxServerError();
    }
}