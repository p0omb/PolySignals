package ru.gbp.PolySignals.client.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraderLeaderboardEntry {
    private Integer rank;
    private String proxyWallet;
    private String userName;
    private BigDecimal vol;
    private BigDecimal pnl;
    private String profileImage;
    private String xUsername;
    private Boolean verifiedBadge;
}

