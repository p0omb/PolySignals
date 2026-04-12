package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DataApiClient {

    private static final String TOP_WHALES_ENDPOINT = "/leaderboard/users";
    private static final String USER_TRADES_ENDPOINT = "/users/%s/trades";

    private final WebClient dataApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    public List<LeaderboardUserDto> getTopWhales(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        return polymarketRetryTemplate.execute(context ->
                dataApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(TOP_WHALES_ENDPOINT)
                                .queryParam("limit", limit)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> PolymarketApiErrorHandler.buildException(
                                        response.statusCode().value(),
                                        body,
                                        TOP_WHALES_ENDPOINT,
                                        response.headers().asHttpHeaders()
                                )))
                        .bodyToFlux(LeaderboardUserDto.class)
                        .collectList()
                        .onErrorMap(WebClientRequestException.class,
                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, TOP_WHALES_ENDPOINT))
                        .block()
        );
    }

    public List<UserTradeDto> getUserTrades(String userId, Instant afterTimestamp, int limit) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(afterTimestamp, "afterTimestamp must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        final String endpoint = USER_TRADES_ENDPOINT.formatted(userId);
        return polymarketRetryTemplate.execute(context ->
                dataApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/users/{userId}/trades")
                                .queryParam("limit", limit)
                                .queryParam("after_timestamp", afterTimestamp.getEpochSecond())
                                .build(userId))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> PolymarketApiErrorHandler.buildException(
                                        response.statusCode().value(),
                                        body,
                                        endpoint,
                                        response.headers().asHttpHeaders()
                                )))
                        .bodyToFlux(UserTradeDto.class)
                        .collectList()
                        .onErrorMap(WebClientRequestException.class,
                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, endpoint))
                        .block()
        );
    }
}