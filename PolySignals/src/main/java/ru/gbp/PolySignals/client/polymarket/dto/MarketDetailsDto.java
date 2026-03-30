package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketDetailsDto {

    @JsonAlias({"id", "marketId", "market_id"})
    private String marketId;

    private String question;
    private String slug;

    @JsonAlias({"url", "marketUrl", "market_url"})
    private String url;

    @JsonAlias({"active", "isActive"})
    private Boolean active;

    @JsonAlias({"closed", "isClosed"})
    private Boolean closed;

    @JsonAlias({"endDate", "end_date"})
    private Instant endDate;

    @JsonAlias({"outcomes", "outcomeNames"})
    private List<String> outcomes;

    @JsonAlias({"outcomePrices", "prices"})
    private List<String> outcomePrices;
}
