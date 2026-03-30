package ru.gbp.PolySignals;

import java.math.BigDecimal;

public interface SignalCandidateProjection {
    String getMarketId();
    String getOutcome();
    Integer getWhalesCount();
    BigDecimal getTotalAmount();
}
