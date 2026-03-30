package ru.gbp.PolySignals.client.polymarket;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DataApiClient {

    private final WebClient dataApiWebClient;

    @Retryable(
            value = { WebClientResponseException.class, IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public List<LeaderboardUserDto> getTopWhales(int limit) {
        return dataApiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/leaderboard/users")
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals, this::handle429)
                .bodyToFlux(LeaderboardUserDto.class)
                .collectList()
                .block();
    }

    @Retryable(
            value = { WebClientResponseException.class, IOException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public List<UserTradeDto> getUserTrades(String userId, Instant afterTimestamp, int limit) {
        return dataApiWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/users/{userId}/trades")
                        .queryParam("limit", limit)
                        .queryParam("after_timestamp", afterTimestamp.getEpochSecond())
                        .build(userId))
                .retrieve()
                .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals, this::handle429)
                .bodyToFlux(UserTradeDto.class)
                .collectList()
                .block();
    }

    private Mono<? extends Throwable> handle429(ClientResponse response) {
        // Можно прочитать Retry-After, подождать, кинуть кастомное исключение для Retry
        return response.headers().asHttpHeaders().containsKey("Retry-After")
                ? Mono.error(new RateLimitException("429 from Data API"))
                : Mono.error(new RateLimitException("429 without Retry-After"));
    }
}