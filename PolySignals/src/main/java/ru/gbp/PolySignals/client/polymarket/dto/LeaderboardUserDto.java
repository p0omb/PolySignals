package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeaderboardUserDto {

    @JsonAlias({"userId", "user_id", "user"})
    private String userId;

    @JsonAlias({"userName", "user_name", "username", "name"})
    private String userName;

    @JsonAlias({"volume", "totalVolume", "total_volume"})
    private BigDecimal volume;

    @JsonAlias({"profit", "totalProfit", "total_profit", "pnl"})
    private BigDecimal profit;
}
