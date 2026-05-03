package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    private List<String> clobTokenIds;

    @JsonProperty("outcomes")
    @JsonAlias({"outcomeNames", "outcome_names"})
    @JsonDeserialize(using = FlexibleStringListDeserializer.class)
    private List<String> outcomes;

    public static final class FlexibleStringListDeserializer extends JsonDeserializer<List<String>> {

        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p == null || p.getCurrentToken() == null) {
                return List.of();
            }

            try {
                if (p.getCurrentToken().isStructStart()) {
                    return p.readValueAs(new TypeReference<List<String>>() {});
                }

                if (p.getCurrentToken().isScalarValue()) {
                    String text = p.getValueAsString();
                    if (text == null || text.isBlank()) {
                        return List.of();
                    }

                    text = text.trim();
                    if (text.startsWith("[") && text.endsWith("]")) {
                        return p.getCodec().readValue(p.getCodec().getFactory().createParser(text), new TypeReference<List<String>>() {});
                    }

                    return List.of(text);
                }

                return List.of();
            } catch (JsonProcessingException e) {
                return List.of();
            } catch (Exception e) {
                return List.of();
            }
        }
    }

//    @JsonAlias({"outcomePrices", "prices"})
//    private List<String> outcomePrices;
}
