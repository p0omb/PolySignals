package ru.gbp.PolySignals.client.telegram;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Component
public class TelegramMessageFormatter {

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(MOSCOW_ZONE);

    public String formatSignal(SignalNotificationPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        String event = firstNotBlank(payload.marketQuestion(), payload.marketId());
        String outcome = payload.outcome();
        String whales = formatWhales(payload.whaleLabels());
        String reason = payload.reason();

        StringBuilder message = new StringBuilder(256);
        message.append("<b>🐋 Multi-whale signal</b>\n\n");
        message.append(lineText("Event", event));
        message.append(lineText("Outcome", outcome));
        message.append(lineText("Whales matched", String.valueOf(payload.whalesCount())));
        message.append(lineText("Total amount", formatAmount(payload.totalAmount())));
        message.append(lineText("Window", formatInstant(payload.firstBetAt()) + " — " + formatInstant(payload.lastBetAt())));
        message.append(lineText("Whales", whales));

        if (reason != null) {
            message.append(lineText("Reason", reason));
        }
        if (hasText(payload.marketUrl())) {
            message.append(lineHtml("Link", buildLink(payload.marketUrl())));
        }

        return message.toString().trim();
    }

    private String lineText(String label, String value) {
        return "<b>" + escapeHtml(label) + ":</b> " + escapeHtml(value) + "\n";
    }

    private String lineHtml(String label, String rawHtmlValue) {
        return "<b>" + escapeHtml(label) + ":</b> " + rawHtmlValue + "\n";
    }

    private String formatWhales(List<String> whaleLabels) {
        if (whaleLabels == null || whaleLabels.isEmpty()) {
            return "n/a";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (String label : whaleLabels) {
            if (hasText(label)) {
                joiner.add(label);
            }
        }

        String joined = joiner.toString();
        return joined.isBlank() ? "n/a" : joined;
    }

    private String buildLink(String marketUrl) {
        return "<a href=\"" + escapeHtmlAttribute(marketUrl) + "\">Open market</a>";
    }

    private String formatAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String firstNotBlank(String primary, String fallback) {
        if (hasText(primary)) {
            return primary;
        }
        return fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private String escapeHtmlAttribute(String value) {
        return escapeHtml(value);
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "n/a";
        }
        return DATE_TIME_FORMATTER.format(instant);
    }
}
