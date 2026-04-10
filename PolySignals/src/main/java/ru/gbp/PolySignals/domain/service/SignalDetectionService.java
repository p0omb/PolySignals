package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.event.SignalCreatedEvent;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.MarketOutcomeSignalProjection;
import ru.gbp.PolySignals.domain.repository.SignalCandidateProjection;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalDetectionService {

    private static final int MIN_WHALES = 2;

    private final BetRepository betRepository;
    private final SignalRepository signalRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void checkSignalForNewBet(Bet newBet, Duration signalWindow, Duration cooldown) {
        Objects.requireNonNull(newBet, "newBet must not be null");
        validatePositiveDuration("signalWindow", signalWindow);
        validatePositiveDuration("cooldown", cooldown);

        Instant to = resolveSignalTime(newBet);
        Instant from = to.minus(signalWindow);

        processMarketOutcome(newBet.getMarketId(), newBet.getOutcome(), from, to, signalWindow, cooldown);
    }

    public void detectAndSendSignals(Duration period, Duration cooldown) {
        validatePositiveDuration("period", period);
        validatePositiveDuration("cooldown", cooldown);

        Instant to = Instant.now();
        Instant from = to.minus(period);

        List<SignalCandidateProjection> candidates = betRepository.findSignalCandidates(from, to, MIN_WHALES);
        for (SignalCandidateProjection candidate : candidates) {
            processMarketOutcome(candidate.getMarketId(), candidate.getOutcome(), from, to, period, cooldown);
        }
    }

    private void processMarketOutcome(
            String marketId,
            String outcome,
            Instant from,
            Instant to,
            Duration signalWindow,
            Duration cooldown
    ) {
        validateMarketAndOutcome(marketId, outcome);
        validatePositiveDuration("signalWindow", signalWindow);
        validatePositiveDuration("cooldown", cooldown);

        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("invalid time window");
        }

        MarketOutcomeSignalProjection stats = betRepository.findMarketOutcomeSignalStats(
                marketId.trim(),
                outcome.trim(),
                from,
                to
        );

        if (stats == null || stats.getWhalesCount() == null || stats.getWhalesCount() < MIN_WHALES) {
            return;
        }

        Signal signal = new Signal();
        signal.setMarketId(stats.getMarketId());
        signal.setOutcome(stats.getOutcome());
        signal.setWhalesCount(Math.toIntExact(stats.getWhalesCount()));
        signal.setTotalAmount(Objects.requireNonNullElse(stats.getTotalAmount(), BigDecimal.ZERO));
        signal.setFirstBetAt(stats.getFirstBetAt());
        signal.setLastBetAt(stats.getLastBetAt());
        signal.setReason(buildReason(stats));
        signal.setCreatedAt(to);
        signal.setTelegramSent(false);
        signal.setPeriodHours(Math.toIntExact(signalWindow.toHours()));
        signal.setSignalKey(buildSignalKey(stats.getMarketId(), stats.getOutcome(), to, cooldown));

        Signal savedSignal;
        try {
            savedSignal = signalRepository.saveAndFlush(signal);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateSignalKeyViolation(ex)) {
                log.debug("Duplicate signal skipped by unique signal_key: {}", signal.getSignalKey());
                return;
            }
            throw ex;
        }

        eventPublisher.publishEvent(new SignalCreatedEvent(savedSignal.getId()));
    }

    private String buildReason(MarketOutcomeSignalProjection stats) {
        return "Matched " + stats.getWhalesCount() + " whales on same market/outcome";
    }

    private String buildSignalKey(String marketId, String outcome, Instant signalTime, Duration cooldown) {
        validateMarketAndOutcome(marketId, outcome);
        validatePositiveDuration("cooldown", cooldown);

        long cooldownSeconds = cooldown.getSeconds();
        long bucketStartEpoch = (signalTime.getEpochSecond() / cooldownSeconds) * cooldownSeconds;

        return normalizeMarketId(marketId) + ":" + normalizeOutcome(outcome) + ":" + bucketStartEpoch;
    }

    private String normalizeMarketId(String marketId) {
        return marketId.trim();
    }

    private String normalizeOutcome(String outcome) {
        return outcome.trim().toUpperCase(Locale.ROOT);
    }

    private Instant resolveSignalTime(Bet newBet) {
        if (newBet.getTransactionTime() != null) {
            return newBet.getTransactionTime();
        }
        if (newBet.getCreatedAt() != null) {
            return newBet.getCreatedAt();
        }
        return Instant.now();
    }

    private void validateMarketAndOutcome(String marketId, String outcome) {
        if (marketId == null || marketId.trim().isEmpty()) {
            throw new IllegalArgumentException("marketId must not be blank");
        }
        if (outcome == null || outcome.trim().isEmpty()) {
            throw new IllegalArgumentException("outcome must not be blank");
        }
    }

    private void validatePositiveDuration(String name, Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.getSeconds() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private boolean isDuplicateSignalKeyViolation(DataIntegrityViolationException ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("signal_key")) {
                return true;
            }
            if (current instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String constraintName = cve.getConstraintName();
                if (constraintName != null && constraintName.contains("signal_key")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
