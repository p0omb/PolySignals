package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserTradeDto {

    @JsonAlias({"marketId", "conditionId", "condition_id"})
    private String marketId;

    // Основное поле исхода (Yes/No/Up/Down и т.п.)
    @JsonAlias({"outcome"})
    private String outcome;

    // Числовой идентификатор исхода, иногда приходит вместо outcome
    @JsonAlias({"asset"})
    private String asset;

    @JsonAlias({"size", "amount", "tradeAmount"})
    private BigDecimal size;

    @JsonAlias({"transactionHash", "transaction_hash", "txHash", "tx_hash"})
    private String transactionHash;

    private Instant timestamp;

    @JsonSetter("outcome")
    public void setOutcomeRaw(String outcome) {
        this.outcome = normalizeOutcome(outcome);
    }

    @JsonSetter("asset")
    public void setAssetRaw(Object raw) {
        this.asset = raw == null ? null : String.valueOf(raw);
        // fallback только если outcome не задан
        if (this.outcome == null || this.outcome.isBlank()) {
            this.outcome = this.asset;
        }
    }

    @JsonAlias({"slug"})
    private String slug;

    @JsonSetter("timestamp")
    public void setTimestampRaw(Object raw) {
        this.timestamp = parseTimestamp(raw);
    }

    @JsonSetter("time")
    public void setTimeRaw(Object raw) {
        this.timestamp = parseTimestamp(raw);
    }

    @JsonSetter("created_at")
    public void setCreatedAtRaw(Object raw) {
        this.timestamp = parseTimestamp(raw);
    }

    private String normalizeOutcome(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant parseTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }

        if (raw instanceof Number n) {
            long value = n.longValue();
            return String.valueOf(Math.abs(value)).length() <= 10
                    ? Instant.ofEpochSecond(value)
                    : Instant.ofEpochMilli(value);
        }

        if (raw instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (trimmed.matches("^-?\\d+$")) {
                long value = Long.parseLong(trimmed);
                return String.valueOf(Math.abs(value)).length() <= 10
                        ? Instant.ofEpochSecond(value)
                        : Instant.ofEpochMilli(value);
            }
            return Instant.parse(trimmed);
        }

        throw new IllegalArgumentException("Unsupported timestamp format: " + raw);
    }
}
