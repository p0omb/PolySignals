package ru.gbp.PolySignals.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "signal", indexes = {
        @Index(name = "idx_signal_market_outcome_time", columnList = "market_id, outcome, created_at")
})
@Data
public class Signal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "signal_key", nullable = false, unique = true, length = 255)
    private String signalKey;

    @Column(name = "market_id", nullable = false, length = 128)
    private String marketId;

    @Column(name = "market_question", length = 512)
    private String marketQuestion;

    @Column(name = "market_url", length = 512)
    private String marketUrl;

    @Column(name = "outcome", nullable = false, length = 255)
    private String outcome;

    @Column(name = "whales_count", nullable = false)
    private Integer whalesCount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal totalAmount;

    @Column(name = "period_hours", nullable = false)
    private Integer periodHours;

    @Column(name = "first_bet_at", nullable = false)
    private Instant firstBetAt;

    @Column(name = "last_bet_at", nullable = false)
    private Instant lastBetAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "telegram_sent", nullable = false)
    private Boolean telegramSent = false;

    @Column(name = "telegram_sent_at")
    private Instant telegramSentAt;

    @Column(name = "reason", length = 512)
    private String reason;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signal_whale_labels", joinColumns = @JoinColumn(name = "signal_id"))
    @Column(name = "label")
    private List<String> whaleLabels = new ArrayList<>();

    @Column(name = "slug")
    private String slug;

    @Column(name = "score")
    private Double score;

    @Column(name = "market_volume")
    private BigDecimal marketVolume;

    @Column(name = "market_liquidity")
    private BigDecimal marketLiquidity;

    @Column(name = "opposite_volume")
    private BigDecimal oppositeVolume;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "auto_bet_placed", nullable = false)
    private Boolean autoBetPlaced = false;

    @Column(name = "whale_weight")
    private Double whaleWeight;
}