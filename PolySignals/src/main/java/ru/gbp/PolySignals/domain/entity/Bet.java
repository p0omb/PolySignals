package ru.gbp.PolySignals.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bet", indexes = {
        @Index(name = "idx_bet_market_outcome_time", columnList = "market_id, outcome, transaction_time"),
        @Index(name = "idx_bet_whale_id", columnList = "whale_id")
})
@Data
public class Bet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "whale_id", nullable = false)
    private Whale whale;

    @Column(name = "market_id", nullable = false, length = 128)
    private String marketId;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome; // "YES"/"NO" или аналог

    @Column(name = "amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal amount;

    @Column(name = "transaction_hash", length = 128)
    private String transactionHash;

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "unique_key", nullable = false, unique = true, length = 256)
    private String uniqueKey;

    @Transient
    private String slug;
}
