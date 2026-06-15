package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import ru.gbp.PolySignals.client.telegram.TelegramMessageFormatter;
import ru.gbp.PolySignals.client.telegram.SignalNotificationPayload;

class TelegramBotTest {

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void formatsTelegramMessage() {
        SignalNotificationPayload payload = new SignalNotificationPayload(
                1L,
                "m1",
                "Question <test>",
                "https://example.com",
                "yes",
                2,
                new BigDecimal("123.45"),
                Instant.parse("2026-01-01T10:00:00Z"),
                Instant.parse("2026-01-01T11:00:00Z"),
                List.of("whale-1", "whale-2"),
                "reason <x>",
                2.0
        );

        String message = formatter.formatSignal(payload);

        assertThat(message).contains("Multi-whale signal");
        assertThat(message).contains("&lt;test&gt;");
        assertThat(message).contains("reason &lt;x&gt;");
        assertThat(message).contains("https://example.com");
    }
}

