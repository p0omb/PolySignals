package ru.gbp.PolySignals.domain.repository;

import java.math.BigDecimal;
import java.time.Instant;

public interface MarketOutcomeSignalProjection {

    String getMarketId();

    String getOutcome();

    Long getWhalesCount();

    BigDecimal getTotalAmount();

    Instant getFirstBetAt();

    Instant getLastBetAt();

    Double getWhaleWeight();
}

