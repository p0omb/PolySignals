package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для представления ответа API /holders для конкретного маркета.
 * Содержит информацию о токене (исходе) и списке топ-холдеров этого токена.
 *
 * Пример JSON из API /holders:
 * {
 *   "token": "0x1234...",
 *   "holders": [
 *     {
 *       "proxyWallet": "0x56687bf447db6ffa42ffe2204a05edaa20f55839",
 *       "name": "imnotawizard",
 *       "amount": 1500.50,
 *       ...
 *     },
 *     ...
 *   ]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketHoldersResponseDto {

    @JsonProperty("token")
    @JsonAlias({"tokenId", "token_id", "asset", "conditionId", "condition_id"})
    private String token;

    @JsonProperty("holders")
    @JsonAlias({"addresses", "balances"})
    private List<HolderDto> holders;
}

