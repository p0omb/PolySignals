//package ru.gbp.PolySignals.client.polymarket;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatusCode;
//import org.springframework.retry.support.RetryTemplate;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//import org.springframework.web.reactive.function.client.WebClientRequestException;
//import ru.gbp.PolySignals.client.polymarket.dto.TraderLeaderboardEntry;
//import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
//
//import java.time.Instant;
//import java.util.List;
//import java.util.Objects;
//
//@Service
//@RequiredArgsConstructor
//public class DataApiClient {
//
//    private static final String TOP_WHALES_ENDPOINT = "/v1/leaderboard";
//    private static final String USER_TRADES_ENDPOINT = "/users/%s/trades";
//
//    private final WebClient dataApiWebClient;
//    private final RetryTemplate polymarketRetryTemplate;
//
//    // Дефолтный вариант вызова (можно использовать без параметров)
//    public List<TraderLeaderboardEntry> getTopWhales() {
//        return getTopWhales(100, 0, "PNL", "WEEK", "OVERALL");
//    }
//
//    // Основной метод с параметрами leaderboard API
//    public List<TraderLeaderboardEntry> getTopWhales(int limit, int offset, String orderBy, String timePeriod, String category) {
//        if (limit <= 0) {
//            throw new IllegalArgumentException("limit must be > 0");
//        }
//        if (offset < 0) {
//            throw new IllegalArgumentException("offset must be >= 0");
//        }
//
//        return polymarketRetryTemplate.execute(context ->
//                dataApiWebClient.get()
//                        .uri(uriBuilder -> uriBuilder
//                                .path(TOP_WHALES_ENDPOINT)
//                                .queryParam("limit", limit)
//                                .queryParam("offset", offset)
//                                .queryParam("orderBy", orderBy)
//                                .queryParam("timePeriod", timePeriod)
//                                .queryParam("category", category)
//                                .build())
//                        .retrieve()
//                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
//                                .defaultIfEmpty("")
//                                .map(body -> PolymarketApiErrorHandler.buildException(
//                                        response.statusCode().value(),
//                                        body,
//                                        TOP_WHALES_ENDPOINT,
//                                        response.headers().asHttpHeaders()
//                                )))
//                        .bodyToFlux(TraderLeaderboardEntry.class)
//                        .collectList()
//                        .onErrorMap(WebClientRequestException.class,
//                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, TOP_WHALES_ENDPOINT))
//                        .block()
//        );
//    }
//
//    public List<UserTradeDto> getUserTrades(String userId, Instant afterTimestamp, int limit) {
//        if (userId == null || userId.isBlank()) {
//            throw new IllegalArgumentException("userId must not be blank");
//        }
//        Objects.requireNonNull(afterTimestamp, "afterTimestamp must not be null");
//        if (limit <= 0) {
//            throw new IllegalArgumentException("limit must be > 0");
//        }
//
//        final String endpoint = USER_TRADES_ENDPOINT.formatted(userId);
//        return polymarketRetryTemplate.execute(context ->
//                dataApiWebClient.get()
//                        .uri(uriBuilder -> uriBuilder
//                                .path(endpoint)
//                                .queryParam("limit", limit)
//                                .queryParam("after_timestamp", afterTimestamp.getEpochSecond())
//                                .build())
//                        .retrieve()
//                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
//                                .defaultIfEmpty("")
//                                .map(body -> PolymarketApiErrorHandler.buildException(
//                                        response.statusCode().value(),
//                                        body,
//                                        endpoint,
//                                        response.headers().asHttpHeaders()
//                                )))
//                        .bodyToFlux(UserTradeDto.class)
//                        .collectList()
//                        .onErrorMap(WebClientRequestException.class,
//                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, endpoint))
//                        .block()
//        );
//    }
//}
package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.client.polymarket.dto.TraderLeaderboardEntry;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DataApiClient {

    private static final String TOP_WHALES_ENDPOINT = "/v1/leaderboard";
    private static final String TRADES_ENDPOINT = "/trades";

    private final WebClient dataApiWebClient;
    private final RetryTemplate polymarketRetryTemplate;

    public List<LeaderboardUserDto> getTopWhales() {
        return getTopWhales(100, 0, "PNL", "WEEK", "OVERALL");
    }

    public List<LeaderboardUserDto> getTopWhales(int limit) {
        return getTopWhales(limit, 0, "PNL", "WEEK", "OVERALL");
    }

    public List<LeaderboardUserDto> getTopWhales(int limit, int offset, String orderBy, String timePeriod, String category) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }

        return polymarketRetryTemplate.execute(context ->
                dataApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(TOP_WHALES_ENDPOINT)
                                .queryParam("limit", limit)
                                .queryParam("offset", offset)
                                .queryParam("orderBy", orderBy)
                                .queryParam("timePeriod", timePeriod)
                                .queryParam("category", category)
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
                        .bodyToFlux(TraderLeaderboardEntry.class)
                        .map(this::toLeaderboardUserDto)
                        .collectList()
                        .onErrorMap(WebClientRequestException.class,
                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, TOP_WHALES_ENDPOINT))
                        .block()
        );
    }

    public List<UserTradeDto> getUserTrades(String userAddress, int limit, int offset) {
        if (userAddress == null || userAddress.isBlank()) {
            throw new IllegalArgumentException("userAddress must not be blank");
        }
        if (limit <= 0 || limit > 10000) {
            throw new IllegalArgumentException("limit must be between 1 and 10000");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }

        return polymarketRetryTemplate.execute(context ->
                dataApiWebClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path(TRADES_ENDPOINT)
                                .queryParam("user", userAddress)
                                .queryParam("limit", limit)
                                .queryParam("offset", offset)
                                .queryParam("takerOnly", true)
                                .build())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> PolymarketApiErrorHandler.buildException(
                                        response.statusCode().value(),
                                        body,
                                        TRADES_ENDPOINT,
                                        response.headers().asHttpHeaders()
                                )))
                        .bodyToFlux(UserTradeDto.class)
                        .collectList()
                        .onErrorMap(WebClientRequestException.class,
                                ex -> PolymarketApiErrorHandler.fromTransportError(ex, TRADES_ENDPOINT))
                        .block()
        );
    }

    public List<UserTradeDto> getUserTradesSince(String userAddress, Instant sinceTimestamp, int maxResults) {
        Objects.requireNonNull(sinceTimestamp, "sinceTimestamp must not be null");
        return getUserTrades(userAddress, maxResults, 0).stream()
                .filter(trade -> trade.getTimestamp() != null && trade.getTimestamp().isAfter(sinceTimestamp))
                .toList();
    }

    private LeaderboardUserDto toLeaderboardUserDto(TraderLeaderboardEntry entry) {
        LeaderboardUserDto dto = new LeaderboardUserDto();
        dto.setUserId(entry.getProxyWallet());
        dto.setUserName(entry.getUserName());
        dto.setVolume(entry.getVol());
        dto.setProfit(entry.getPnl());
        return dto;
    }
}