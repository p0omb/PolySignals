package ru.gbp.PolySignals.domain.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "whale", indexes = {
        @Index(name = "idx_whale_user_id", columnList = "user_id", unique = true)
})
@Data
public class Whale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 128)
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "volume")
    private BigDecimal volume;

    @Column(name = "profit")
    private BigDecimal profit;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "ignored")
    private Boolean ignored = false;
}