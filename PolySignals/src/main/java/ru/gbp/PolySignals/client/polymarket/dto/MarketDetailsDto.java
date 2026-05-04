package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDetailsDto {

    @JsonProperty("id")
    @JsonAlias({"marketId", "market_id", "conditionId", "condition_id"})
    private String marketId;

    @JsonProperty("question")
    @JsonAlias({"marketQuestion", "market_question"})
    private String question;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("url")
    @JsonAlias({"marketUrl", "market_url"})
    private String url;

    @JsonAlias({"active", "isActive"})
    private Boolean active;

    @JsonAlias({"closed", "isClosed"})
    private Boolean closed;

    @JsonAlias({"endDate", "end_date"})
    private Instant endDate;

    @JsonProperty("clobTokenIds")
    @JsonAlias({"clob_token_ids", "tokenIds", "token_ids"})
    private String clobTokenIdsRaw;   // строка вида "[\"token1\",\"token2\"]"

    // Парсинг строки в список токенов
    @JsonIgnore
    public List<String> getClobTokenIds() {
        if (clobTokenIdsRaw == null || clobTokenIdsRaw.isBlank()) {
            return List.of();
        }
        String trimmed = clobTokenIdsRaw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String content = trimmed.substring(1, trimmed.length() - 1);
            if (content.isBlank()) return List.of();
            String[] parts = content.split(",");
            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String cleaned = part.trim().replaceAll("^\"|\"$", "");
                if (!cleaned.isEmpty()) {
                    result.add(cleaned);
                }
            }
            return result;
        }
        return List.of(trimmed);
    }

//    @JsonAlias({"outcomePrices", "prices"})
//    private List<String> outcomePrices;
}
