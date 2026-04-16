package ru.gbp.PolySignals.config;

import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.gbp.PolySignals.config.properties.PolymarketRetryProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetryConfigTest {

    @Test
    void retriesOn5xxAnd429ButNotOn404() {
        PolymarketRetryProperties props = mock(PolymarketRetryProperties.class);
        when(props.getMaxAttempts()).thenReturn(3);
        when(props.getInitialDelay()).thenReturn(Duration.ofMillis(1));
        when(props.getMultiplier()).thenReturn(1.0);

        RetryTemplate template = new RetryConfig(props).polymarketRetryTemplate();

        AtomicInteger attempts500 = new AtomicInteger();
        WebClientResponseException ex500 = WebClientResponseException.create(
                500, "Internal Server Error", null, new byte[0], StandardCharsets.UTF_8
        );
        assertThrows(WebClientResponseException.class, () ->
                template.execute(ctx -> {
                    attempts500.incrementAndGet();
                    throw ex500;
                })
        );
        assertEquals(3, attempts500.get());

        AtomicInteger attempts429 = new AtomicInteger();
        WebClientResponseException ex429 = WebClientResponseException.create(
                429, "Too Many Requests", null, new byte[0], StandardCharsets.UTF_8
        );
        assertThrows(WebClientResponseException.class, () ->
                template.execute(ctx -> {
                    attempts429.incrementAndGet();
                    throw ex429;
                })
        );
        assertEquals(3, attempts429.get());

        AtomicInteger attempts404 = new AtomicInteger();
        WebClientResponseException ex404 = WebClientResponseException.create(
                404, "Not Found", null, new byte[0], StandardCharsets.UTF_8
        );
        assertThrows(WebClientResponseException.class, () ->
                template.execute(ctx -> {
                    attempts404.incrementAndGet();
                    throw ex404;
                })
        );
        assertEquals(1, attempts404.get());
    }
}

