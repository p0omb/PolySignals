package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDetailsDto {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonProperty("id")
    private String marketId;

    @JsonProperty("conditionId")
    private String conditionId;

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
    private String endDateRaw;

    @JsonProperty("clobTokenIds")
    @JsonAlias({"clob_token_ids", "tokenIds", "token_ids"})
    private Object clobTokenIdsRaw;

    @JsonProperty("outcomes")
    @JsonAlias({"outcomeNames", "outcome_names"})
    private Object outcomesRaw;

    @JsonIgnore
    public List<String> getClobTokenIds() {
        return parseToStringList(clobTokenIdsRaw);
    }

    @JsonIgnore
    public List<String> getOutcomes() {
        return parseToStringList(outcomesRaw);
    }

    /**
     * Compatibility helper for existing code.
     */
    @JsonIgnore
    public String getConditionId() {
        return this.conditionId;
    }

    private List<String> parseToStringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        try {
            if (raw instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item != null)
                        .map(Object::toString)
                        .toList();
            }

            String value = raw.toString().trim();
            if (value.isEmpty()) {
                return Collections.emptyList();
            }

            if (value.startsWith("[")) {
                return OBJECT_MAPPER.readValue(value, new TypeReference<List<String>>() {});
            }

            return List.of(value);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }
}