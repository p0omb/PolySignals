package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.client.polymarket.GammaApiClient;
import ru.gbp.PolySignals.client.polymarket.HoldersApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
import ru.gbp.PolySignals.config.properties.SignalProperties;
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
    private final HoldersApiClient holdersApiClient;
    private final SignalProperties signalProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = DataIntegrityViolationException.class)
    public void checkSignalForNewBet(Bet newBet, Duration signalWindow, Duration cooldown) {
        Objects.requireNonNull(newBet, "newBet must not be null");
        validatePositiveDuration("signalWindow", signalWindow);
        validatePositiveDuration("cooldown", cooldown);

        log.info("checkSignalForNewBet started for bet id={}, market={}, outcome={}",
                newBet.getId(), newBet.getMarketId(), newBet.getOutcome());

        Instant to = resolveSignalTime(newBet);
        Instant from = to.minus(signalWindow);

        log.info("Calling processMarketOutcome for market={}, outcome={}", newBet.getMarketId(), newBet.getOutcome());
        processMarketOutcome(newBet.getMarketId(), newBet.getOutcome(), from, to, signalWindow, cooldown, newBet.getSlug(), newBet.getTitle());
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
            processMarketOutcome(candidate.getMarketId(), candidate.getOutcome(), from, to, period, cooldown, null, null);
        }
    }


    private void processMarketOutcome(
            String marketId,
            String outcome,
            Instant from,
            Instant to,
            Duration signalWindow,
            Duration cooldown,
            String slug,
            String title
    ) {
        log.info("processMarketOutcome: marketId={}, outcome={}, from={}, to={}", marketId, outcome, from, to);

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

        if (stats != null) {
            log.info("Stats: whalesCount={}, totalAmount={}", stats.getWhalesCount(), stats.getTotalAmount());
        } else {
            log.info("Stats: null for marketId={}, outcome={}", marketId, outcome);
        }

        if (stats == null || stats.getWhalesCount() == null || stats.getWhalesCount() < MIN_WHALES) {
            return;
        }

        if (signalProperties.getMinTotalAmount().compareTo(BigDecimal.ZERO) > 0
                && stats.getTotalAmount().compareTo(signalProperties.getMinTotalAmount()) < 0) {
            log.info("Signal ignored: totalAmount {} < minTotalAmount {}", stats.getTotalAmount(), signalProperties.getMinTotalAmount());
            return;
        }

        // Фильтрация по топ-холдерам противоположного исхода
        if (Boolean.TRUE.equals(signalProperties.getFilterOppositeHolders())) {
            String signalOutcome = normalizeOutcome(stats.getOutcome());
            if (isFilteredByOppositeHolders(normalizeMarketId(stats.getMarketId()), signalOutcome)) {
                log.info("Signal ignored: opposite outcome has strong holders for market={}, outcome={}",
                        stats.getMarketId(), signalOutcome);
                return;
            }
        }

        String normalizedMarketId = normalizeMarketId(stats.getMarketId());
        String normalizedOutcome = normalizeOutcome(stats.getOutcome());

        List<String> whaleLabels = betRepository
                .findDistinctWhaleNamesByMarketOutcomeAndTimeRange(normalizedMarketId, normalizedOutcome, from, to)
                .stream()
                .filter(this::hasText)
                .distinct()
                .collect(Collectors.toList());

        if (signalProperties.getExcludedTitlePatterns() != null && !signalProperties.getExcludedTitlePatterns().isEmpty()) {
            String titleLower = title != null ? title.toLowerCase() : "";
            boolean excluded = signalProperties.getExcludedTitlePatterns().stream()
                    .anyMatch(pattern -> titleLower.contains(pattern.toLowerCase()));
            if (excluded) {
                log.info("Signal ignored: title '{}' matches excluded pattern", title);
                return;
            }
        }

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
        signal.setSlug(slug);
        signal.setMarketQuestion(title);

        log.debug(
                "Signal key built: key={}, marketId={}, outcome={}, from={}, to={}, cooldown={}",
                signalKey, normalizedMarketId, normalizedOutcome, from, to, cooldown
        );

        log.info("Processing market outcome: marketId={}, outcome={}, from={}, to={}", marketId, outcome, from, to);
//        try {
//            log.info("Calling Gamma API for marketId={}", normalizedMarketId);
//            MarketDetailsDto market = gammaApiClient.getMarket(normalizedMarketId);
//            log.info("Gamma API returned market: {}", market != null ? market.getQuestion() : "null");
//
//            if (market != null) {
//                if (hasText(market.getQuestion())) {
//                    signal.setMarketQuestion(market.getQuestion().trim());
//                }
//                signal.setMarketUrl(resolveMarketUrl(market, normalizedMarketId));
//            } else {
//                signal.setMarketUrl("https://polymarket.com/event/" + normalizedMarketId);
//            }
//        } catch (Exception e) {
//            signal.setMarketUrl("https://polymarket.com/event/" + normalizedMarketId);
//            log.error("Gamma API error for marketId={}", normalizedMarketId, e);
//        }

        if (slug != null && !slug.isBlank()) {
            signal.setMarketUrl("https://polymarket.com/event/" + slug);
        } else {
            signal.setMarketUrl("https://polymarket.com/event/" + normalizedMarketId); // fallback
        }

        Signal savedSignal;
        try {
            log.info("Saving signal with key={}, marketId={}, outcome={}", signalKey, normalizedMarketId, normalizedOutcome);
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

    /**
     * Проверяет наличие сильных холдеров противоположного исхода.
     *
     * Фильтрация применяется только если:
     * - рынок бинарный (ровно 2 исхода);
     * - текущий outcome найден в списке outcomes;
     * - есть ровно 2 CLOB tokenIds;
     * - можно однозначно сопоставить current outcome -> opposite token.
     *
     * Во всех неоднозначных случаях возвращает {@code false}, чтобы не терять валидные сигналы.
     */
    private boolean isFilteredByOppositeHolders(String marketId, String outcome) {
        log.debug("isFilteredByOppositeHolders: marketId={}, outcome={}", marketId, outcome);

        try {
            MarketDetailsDto market = gammaApiClient.getMarket(marketId);
            if (market == null) {
                log.debug("Gamma market details are null for marketId={}, skipping opposite-holders filter", marketId);
                return false;
            }

            List<String> clobTokenIds = market.getClobTokenIds();
            if (clobTokenIds == null || clobTokenIds.size() != 2) {
                log.debug("Market {} doesn't have exactly 2 clobTokenIds ({}), skipping filter", marketId, clobTokenIds);
                return false;
            }

            // Попытка получить outcomes из Gamma API
            List<String> outcomes = market.getOutcomes();
            if (outcomes == null || outcomes.size() != 2) {
                log.warn("Market {} doesn't have exactly 2 outcomes ({}), using fallback outcome detection", marketId, outcomes);
                // fallback: используем простую логику по названию исхода
                return isFilteredByOppositeHoldersFallback(marketId, outcome, clobTokenIds);
            }

            // Находим индекс текущего исхода в списке outcomes
            int outcomeIndex = -1;
            for (int i = 0; i < outcomes.size(); i++) {
                if (outcomes.get(i).equalsIgnoreCase(outcome)) {
                    outcomeIndex = i;
                    break;
                }
            }

            if (outcomeIndex == -1) {
                log.warn("Outcome '{}' not found in market outcomes {} for marketId={}", outcome, outcomes, marketId);
                // fallback если исход не найден
                return isFilteredByOppositeHoldersFallback(marketId, outcome, clobTokenIds);
            }

            // Противоположный индекс = 1 - текущий индекс (работает для 2 исходов)
            int oppositeIndex = 1 - outcomeIndex;
            String oppositeTokenId = clobTokenIds.get(oppositeIndex);

            if (!hasText(oppositeTokenId)) {
                log.debug("Opposite tokenId is blank for marketId={}, outcome={}", marketId, outcome);
                return false;
            }

            BigDecimal oppositeVolume = holdersApiClient.calculateOppositeOutcomeVolume(
                    marketId,
                    oppositeTokenId,
                    signalProperties.getOppositeHoldersLimit()
            );

            log.info("Opposite outcome volume for market={}, outcome={}, oppositeOutcome={}, oppositeTokenId={}: {} (threshold: {})",
                    marketId, outcome, outcomes.get(oppositeIndex), oppositeTokenId, oppositeVolume, signalProperties.getOppositeHoldersMinAmount());

            return oppositeVolume.compareTo(signalProperties.getOppositeHoldersMinAmount()) >= 0;

        } catch (Exception e) {
            log.error("Error checking opposite holders for marketId={}, outcome={}: {}",
                    marketId, outcome, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Fallback логика для определения противоположного исхода по названию
     */
    private boolean isFilteredByOppositeHoldersFallback(String marketId, String outcome, List<String> clobTokenIds) {
        log.debug("Using fallback opposite-holders filter for marketId={}, outcome={}", marketId, outcome);

        int oppositeIndex;
        String outcomeUpper = outcome.toUpperCase();
        if (outcomeUpper.contains("YES") || outcomeUpper.contains("UP") || outcomeUpper.contains("OVER")) {
            oppositeIndex = 1; // противоположный – второй токен
        } else {
            oppositeIndex = 0; // противоположный – первый токен
        }

        String oppositeTokenId = clobTokenIds.get(oppositeIndex);
        if (!hasText(oppositeTokenId)) {
            return false;
        }

        BigDecimal oppositeVolume = holdersApiClient.calculateOppositeOutcomeVolume(
                marketId,
                oppositeTokenId,
                signalProperties.getOppositeHoldersLimit()
        );

        log.info("Opposite outcome volume (fallback) for market={}, outcome={}, oppositeTokenId={}: {} (threshold: {})",
                marketId, outcome, oppositeTokenId, oppositeVolume, signalProperties.getOppositeHoldersMinAmount());

        return oppositeVolume.compareTo(signalProperties.getOppositeHoldersMinAmount()) >= 0;
    }
}
