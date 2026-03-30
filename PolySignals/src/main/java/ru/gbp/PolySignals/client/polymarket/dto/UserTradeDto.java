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

    @JsonAlias({"marketId", "market_id", "market"})
    private String marketId;

    @JsonAlias({"outcome", "side"})
    private String outcome;

    @JsonAlias({"size", "amount", "tradeAmount"})
    private BigDecimal size;

    @JsonAlias({"transactionHash", "transaction_hash", "txHash", "tx_hash"})
    private String transactionHash;

    private Instant timestamp;

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

    private Instant parseTimestamp(Object raw) {
        if (raw == null) {
            return null;
        }

        if (raw instanceof Number n) {
            long value = n.longValue();
            // <= 10 digits: epoch seconds, otherwise epoch millis
            return String.valueOf(Math.abs(value)).length() <= 10
                    ? Instant.ofEpochSecond(value)
                    : Instant.ofEpochMilli(value);
        }

        if (raw instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (trimmed.matches("^\\d+$")) {
                long value = Long.parseLong(trimmed);
                return trimmed.length() <= 10
                        ? Instant.ofEpochSecond(value)
                        : Instant.ofEpochMilli(value);
            }
            return Instant.parse(trimmed);
        }

        throw new IllegalArgumentException("Unsupported timestamp format: " + raw);
    }
}

