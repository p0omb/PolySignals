package ru.gbp.PolySignals.client.telegram;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SignalNotificationPayload(
        Long signalId,
        String marketId,
        String marketQuestion,
        String marketUrl,
        String outcome,
        Integer whalesCount,
        BigDecimal totalAmount,
        Instant firstBetAt,
        Instant lastBetAt,
        List<String> whaleLabels,
        String reason,
        Double whaleWeight
) {
    public SignalNotificationPayload {
        Objects.requireNonNull(signalId, "signalId must not be null");
        Objects.requireNonNull(marketId, "marketId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(whalesCount, "whalesCount must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(firstBetAt, "firstBetAt must not be null");
        Objects.requireNonNull(lastBetAt, "lastBetAt must not be null");

        if (marketId.isBlank()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }
        if (outcome.isBlank()) {
            throw new IllegalArgumentException("outcome must not be blank");
        }
        if (whalesCount < 2) {
            throw new IllegalArgumentException("whalesCount must be >= 2");
        }
        if (totalAmount.signum() < 0) {
            throw new IllegalArgumentException("totalAmount must be >= 0");
        }
        if (lastBetAt.isBefore(firstBetAt)) {
            throw new IllegalArgumentException("lastBetAt must not be before firstBetAt");
        }

        marketQuestion = normalizeOptional(marketQuestion);
        marketUrl = normalizeOptional(marketUrl);
        reason = normalizeOptional(reason);

        whaleLabels = whaleLabels == null ? List.of() : List.copyOf(whaleLabels);
        if (whaleLabels.size() > whalesCount) {
            throw new IllegalArgumentException("whaleLabels size must not exceed whalesCount");
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
