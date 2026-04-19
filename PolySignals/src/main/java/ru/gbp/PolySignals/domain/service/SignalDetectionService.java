package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.client.polymarket.GammaApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalDetectionService {

    private static final int MIN_WHALES = 2;
    private final AtomicReference<Instant> lastDetectAndSendAt = new AtomicReference<>();

    private final BetRepository betRepository;
    private final SignalRepository signalRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GammaApiClient gammaApiClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        if (shouldSkipDetectAndSend(cooldown)) {
            return;
        }

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

        String normalizedMarketId = normalizeMarketId(stats.getMarketId());
        String normalizedOutcome = normalizeOutcome(stats.getOutcome());

        List<String> whaleLabels = betRepository
                .findDistinctWhaleNamesByMarketOutcomeAndTimeRange(normalizedMarketId, normalizedOutcome, from, to)
                .stream()
                .filter(this::hasText)
                .distinct()
                .collect(Collectors.toList());

        Signal signal = new Signal();
        signal.setMarketId(normalizedMarketId);
        signal.setOutcome(normalizedOutcome);
        signal.setWhalesCount(Math.toIntExact(stats.getWhalesCount()));
        signal.setTotalAmount(Objects.requireNonNullElse(stats.getTotalAmount(), BigDecimal.ZERO));
        signal.setFirstBetAt(stats.getFirstBetAt());
        signal.setLastBetAt(stats.getLastBetAt());
        signal.setReason(buildReason(stats));

        Instant signalTime = Objects.requireNonNullElse(stats.getLastBetAt(), to);
        signal.setCreatedAt(signalTime);

        signal.setTelegramSent(false);
        signal.setPeriodHours(Math.toIntExact(signalWindow.toHours()));
        signal.setWhaleLabels(whaleLabels);

        String signalKey = buildSignalKey(normalizedMarketId, normalizedOutcome, signalTime, cooldown);
        signal.setSignalKey(signalKey);

        log.debug(
                "Signal key built: key={}, marketId={}, outcome={}, from={}, to={}, cooldown={}",
                signalKey, normalizedMarketId, normalizedOutcome, from, to, cooldown
        );

        log.info("Processing market outcome: marketId={}, outcome={}, from={}, to={}", marketId, outcome, from, to);
        try {
            MarketDetailsDto market = gammaApiClient.getMarket(normalizedMarketId);
            log.info("Gamma API returned market: {}", market);
            if (market != null) {
                if (hasText(market.getQuestion())) {
                    signal.setMarketQuestion(market.getQuestion().trim());
                }
                signal.setMarketUrl(resolveMarketUrl(market, normalizedMarketId));
            } else {
                signal.setMarketUrl("https://polymarket.com/event/" + normalizedMarketId);
            }
        } catch (Exception e) {

            signal.setMarketUrl("https://polymarket.com/event/" + normalizedMarketId);
            log.warn("Failed to fetch market details for {}", normalizedMarketId, e);
        }

        Signal savedSignal;
        try {
            savedSignal = signalRepository.saveAndFlush(signal);
        } catch (DataIntegrityViolationException ex) {
            log.error("DataIntegrityViolationException for signalKey={}", signal.getSignalKey(), ex);
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
        long alignedCooldownSeconds = alignCooldownSeconds(cooldownSeconds);
        long bucketStartEpoch = (signalTime.getEpochSecond() / alignedCooldownSeconds) * alignedCooldownSeconds;

        return normalizeMarketId(marketId) + ":" + normalizeOutcome(outcome) + ":" + bucketStartEpoch;
    }

    private long alignCooldownSeconds(long cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        if (cooldownSeconds % 3600 == 0) {
            return cooldownSeconds;
        }
        long aligned = ((cooldownSeconds + 3599) / 3600) * 3600;
        log.warn("Cooldown {}s is not multiple of 3600s, aligned to {}s for stable signal buckets", cooldownSeconds, aligned);
        return aligned;
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean shouldSkipDetectAndSend(Duration cooldown) {
        Instant now = Instant.now();
        Instant prev = lastDetectAndSendAt.get();
        if (prev != null && prev.plus(cooldown).isAfter(now)) {
            log.info("detectAndSendSignals skipped by cooldown guard: lastRun={}, cooldown={}", prev, cooldown);
            return true;
        }
        lastDetectAndSendAt.set(now);
        return false;
    }

    private String resolveMarketUrl(MarketDetailsDto market, String fallbackMarketId) {
        if (market == null) {
            return "https://polymarket.com/event/" + fallbackMarketId;
        }
        if (hasText(market.getSlug())) {
            return "https://polymarket.com/event/" + market.getSlug().trim();
        }
        if (hasText(market.getUrl())) {
            String url = market.getUrl().trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
            return "https://polymarket.com/event/" + url;
        }

        if (hasText(market.getMarketId())) {
            return "https://polymarket.com/event/" + market.getMarketId().trim();
        }
        return "https://polymarket.com/event/" + fallbackMarketId;
    }
}
