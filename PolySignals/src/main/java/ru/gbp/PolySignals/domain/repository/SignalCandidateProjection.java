package ru.gbp.PolySignals.domain.repository;

import java.math.BigDecimal;

public interface SignalCandidateProjection {
    String getMarketId();
    String getOutcome();
    Integer getWhalesCount();
    BigDecimal getTotalAmount();
}
