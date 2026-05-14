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

        assertAttempts(template, 500, 3);
        assertAttempts(template, 503, 3);
        assertAttempts(template, 429, 3);
        assertAttempts(template, 404, 1);
    }

    private static void assertAttempts(RetryTemplate template, int status, int expectedAttempts) {
        AtomicInteger attempts = new AtomicInteger();
        WebClientResponseException ex = WebClientResponseException.create(
                status,
                "status-" + status,
                null,
                new byte[0],
                StandardCharsets.UTF_8
        );

        assertThrows(WebClientResponseException.class, () ->
                template.execute(ctx -> {
                    attempts.incrementAndGet();
                    throw ex;
                })
        );

        assertEquals(expectedAttempts, attempts.get(), "Unexpected attempts for status " + status);
    }
}
