package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private List<String> clobTokenIds;

    @JsonProperty("outcomes")
    @JsonAlias({"outcomeNames", "outcome_names"})
    private List<String> outcomes;

//    @JsonAlias({"outcomePrices", "prices"})
//    private List<String> outcomePrices;
}
