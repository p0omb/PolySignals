package ru.gbp.PolySignals.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signal", indexes = {
        @Index(name = "idx_signal_market_outcome_time", columnList = "market_id, outcome, created_at")
})
@Data
public class Signal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_id", nullable = false, length = 128)
    private String marketId;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "whales_count", nullable = false)
    private Integer whalesCount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal totalAmount;

    @Column(name = "period_hours", nullable = false)
    private Integer periodHours;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}