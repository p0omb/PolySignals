package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для представления холдера (владельца позиции) на определённом исходе маркета.
 * Используется для анализа топ-холдеров противоположного исхода при фильтрации сигналов.
 *
 * Пример JSON из API /holders:
 * {
 *   "proxyWallet": "0x56687bf447db6ffa42ffe2204a05edaa20f55839",
 *   "name": "imnotawizard",
 *   "amount": 1500.50,
 *   "outcomeIndex": 0,
 *   "asset": "0x..."
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HolderDto {

    @JsonProperty("proxyWallet")
    @JsonAlias({"proxy_wallet", "wallet", "address"})
    private String proxyWallet;

    @JsonProperty("name")
    @JsonAlias({"pseudonym", "userName", "user_name"})
    private String name;

    @JsonProperty("amount")
    @JsonAlias({"balance", "size", "quantity"})
    private BigDecimal amount;

    @JsonProperty("outcomeIndex")
    @JsonAlias({"outcome_index", "token_index"})
    private Integer outcomeIndex;

    @JsonProperty("asset")
    @JsonAlias({"token", "tokenId", "token_id", "conditionId", "condition_id"})
    private String asset;
}

