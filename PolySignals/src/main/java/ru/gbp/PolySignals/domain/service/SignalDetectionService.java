package ru.gbp.PolySignals.domain.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final Cache<String, Double> recentScores = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    private final AtomicInteger betsToday = new AtomicInteger(0);
    private volatile Instant lastBetTime = Instant.EPOCH;

    @Value("${signal.max.bets.per.day:18}")
    private int maxBetsPerDay;

    @Value("${signal.min.interval.between.bets.minutes:30}")
    private int minIntervalMinutes;

    @Value("${signal.top.percent:20}")
    private double topPercent;

    @Value("${signal.freshness.half.life.seconds:300}")
    private double freshnessHalfLife;

    @Scheduled(cron = "0 0 10 * * *", zone = "Europe/Moscow" )
    public void resetDailyBets() {
        int prev = betsToday.getAndSet(0);
        log.info("Daily bet counter reset, previous: {}", prev);
    }

    private double computeTopPercentile(double topPercent) {
        List<Double> scores = new ArrayList<>(recentScores.asMap().values());
        if (scores.isEmpty()) return 0.0;
        Collections.sort(scores);
        int index = (int) Math.ceil(scores.size() * (1 - topPercent / 100.0)) - 1;
        if (index < 0) index = 0;
        return scores.get(index);
    }

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
        log.info("processMarketOutcome ENTER: marketId={}, outcome={}, from={}, to={}", marketId, outcome, from, to);

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

        if (stats == null) {
            log.info("processMarketOutcome: stats is null for marketId={}, outcome={}", marketId, outcome);
            return;
        }

        log.info("processMarketOutcome: stats marketId={} outcome={} whalesCount={} totalAmount={} firstBet={} lastBet={}",
                stats.getMarketId(), stats.getOutcome(), stats.getWhalesCount(), stats.getTotalAmount(),
                stats.getFirstBetAt(), stats.getLastBetAt());

        if (stats.getWhalesCount() == null || stats.getWhalesCount() < MIN_WHALES) {
            log.info("Signal ignored: whalesCount={} < MIN_WHALES={} for market={} outcome={}",
                    stats.getWhalesCount(), MIN_WHALES, stats.getMarketId(), stats.getOutcome());
            return;
        }

        if (signalProperties.getMinTotalAmount().compareTo(BigDecimal.ZERO) > 0
                && stats.getTotalAmount().compareTo(signalProperties.getMinTotalAmount()) < 0) {
            log.info("Signal ignored: totalAmount {} < minTotalAmount {} for market={} outcome={}",
                    stats.getTotalAmount(), signalProperties.getMinTotalAmount(), stats.getMarketId(), stats.getOutcome());
            return;
        }

        // --- Проверка фильтра противоположных холдеров ---
        log.info("BEFORE opposite filter: market={} outcome={} whales={} amount={} filterEnabled={}",
                stats.getMarketId(), stats.getOutcome(), stats.getWhalesCount(), stats.getTotalAmount(),
                signalProperties.getFilterOppositeHolders());

        if (Boolean.TRUE.equals(signalProperties.getFilterOppositeHolders())) {
            String signalOutcome = normalizeOutcome(stats.getOutcome());
            log.info("Calling isFilteredByOppositeHolders for market={}, outcome={}", normalizeMarketId(stats.getMarketId()), signalOutcome);
            try {
                boolean filtered = isFilteredByOppositeHolders(normalizeMarketId(stats.getMarketId()), signalOutcome);
                log.info("isFilteredByOppositeHolders returned: {}", filtered);
                if (filtered) {
                    log.info("Signal ignored: opposite outcome has strong holders for market={}, outcome={}",
                            stats.getMarketId(), signalOutcome);
                    return;
                }
            } catch (Exception e) {
                log.warn("Opposite filter threw exception for market={}, outcome={}: {}", stats.getMarketId(), signalOutcome, e.getMessage());
                // fail-open: не фильтруем
            }
        } else {
            log.info("Opposite filter is disabled, skipping.");
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

        // 1. Получаем данные рынка для скоринга (Gamma API кешируется)
        MarketDetailsDto market = null;
        try {
            market = gammaApiClient.getMarket(normalizedMarketId);
        } catch (Exception e) {
            log.warn("Cannot fetch market details for scoring: {}", e.getMessage());
        }

        double volume = 0.0;
        double liquidity = 0.0;
        double oppositeVolume = 0.0;

        if (market != null) {
            volume = market.getVolumeClob() != null ? market.getVolumeClob().doubleValue() : 0.0;
            liquidity = market.getLiquidityClob() != null ? market.getLiquidityClob().doubleValue() : 0.0;
            // Противоположный объём (можно использовать уже существующий метод или упрощённый вариант)
            oppositeVolume = calculateOppositeVolume(market, normalizedOutcome).doubleValue();
        }

        // 2. Фактор свежести
        Instant now = Instant.now();
        long ageSeconds = stats.getLastBetAt() != null
                ? Duration.between(stats.getLastBetAt(), now).toSeconds()
                : 0;
        double freshness = Math.exp(-ageSeconds / freshnessHalfLife);

        // 3. Базовый счёт
        double baseScore = stats.getTotalAmount().doubleValue()
                * stats.getWhalesCount()
                * Math.log10(volume + 1)
                * Math.log10(liquidity + 1);

        // Штраф за сильный перекос холдеров (опционально, но можно)
        double penalty = 1.0;
        if (oppositeVolume > 20000) {
            penalty = 0.4;
        } else if (oppositeVolume > 5000) {
            penalty = 0.7;
        }
        double finalScore = baseScore * penalty * freshness;

        // 4. Обновляем кэш недавних score
        recentScores.put(UUID.randomUUID().toString(), finalScore);

        // 5. Проверка дневного лимита
        if (betsToday.get() >= maxBetsPerDay) {
            log.info("Signal skipped: daily bet limit reached ({})", maxBetsPerDay);
            return;
        }

        // 6. Проверка минимального интервала
        long secondsSinceLastBet = Duration.between(lastBetTime, now).toSeconds();
        if (secondsSinceLastBet < minIntervalMinutes * 60L) {
            log.debug("Signal skipped: too soon after previous bet ({} sec < {} min)",
                    secondsSinceLastBet, minIntervalMinutes);
            return;
        }

        // 7. Динамический порог
        double threshold = computeTopPercentile(topPercent);
        if (finalScore < threshold) {
            log.debug("Signal filtered by dynamic threshold: score {} < {}", finalScore, threshold);
            return;
        }

        // Сигнал прошёл все фильтры – обновляем счётчики
        betsToday.incrementAndGet();
        lastBetTime = now;
        log.info("Signal approved for betting: score={}, threshold={}, bet #{}, market={}",
                finalScore, threshold, betsToday.get(), normalizedMarketId);

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
        signal.setScore(finalScore);

        // Заполняем token_id и рыночные метрики
        if (market != null) {
            signal.setMarketVolume(BigDecimal.valueOf(volume));
            signal.setMarketLiquidity(BigDecimal.valueOf(liquidity));
            signal.setOppositeVolume(BigDecimal.valueOf(oppositeVolume));

            // Находим token_id для выбранного исхода
            List<String> outcomes = market.getOutcomes();
            List<String> tokenIds = market.getClobTokenIds();
            if (outcomes != null && tokenIds != null && outcomes.size() == tokenIds.size()) {
                String targetOutcome = normalizedOutcome; // уже в верхнем регистре
                int idx = -1;
                // сначала точное совпадение
                for (int i = 0; i < outcomes.size(); i++) {
                    if (targetOutcome.equalsIgnoreCase(outcomes.get(i).trim())) {
                        idx = i;
                        break;
                    }
                }
                // если нет — частичное совпадение (для спортивных названий)
                if (idx == -1) {
                    String normTarget = targetOutcome.replaceAll("[^\\p{Alnum}]", "").toLowerCase();
                    for (int i = 0; i < outcomes.size(); i++) {
                        String normCandidate = outcomes.get(i).replaceAll("[^\\p{Alnum}]", "").toLowerCase();
                        if (normCandidate.contains(normTarget) || normTarget.contains(normCandidate)) {
                            idx = i;
                            break;
                        }
                    }
                }
                if (idx >= 0 && idx < tokenIds.size()) {
                    signal.setTokenId(tokenIds.get(idx));
                } else {
                    log.warn("Could not determine token_id for outcome={} in market={}", normalizedOutcome, normalizedMarketId);
                }
            }
        }

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

        // Предварительная проверка на дубль до вставки
        if (signalRepository.findBySignalKey(signalKey).isPresent()) {
            log.debug("Duplicate signal skipped before save: signalKey={}", signalKey);
            return;
        }

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

        if (!hasText(outcome)) {
            log.debug("Skip filter: requestedOutcome is blank for marketId={}", marketId);
            return false; // fail-open
        }

        try {
            MarketDetailsDto market = gammaApiClient.getMarket(marketId);
            if (market == null) {
                log.debug("Gamma market details are null for marketId={}, skip filter", marketId);
                return false;
            }

            List<String> outcomes = market.getOutcomes();
            List<String> clobTokenIds = market.getClobTokenIds();

            if (outcomes == null || outcomes.size() != 2) {
                log.debug("Skip filter: invalid outcomes for marketId={}, outcomes={}", marketId, outcomes);
                return false;
            }
            if (clobTokenIds == null || clobTokenIds.size() != 2) {
                log.debug("Skip filter: invalid clobTokenIds for marketId={}, clobTokenIds={}", marketId, clobTokenIds);
                return false;
            }

            int outcomeIndex = -1;

            // 1) exact match
            for (int i = 0; i < outcomes.size(); i++) {
                String candidate = outcomes.get(i);
                if (hasText(candidate) && candidate.equalsIgnoreCase(outcome)) {
                    outcomeIndex = i;
                    break;
                }
            }

            // 2) partial match for sports names (e.g. "LAKERS" vs "Los Angeles Lakers")
            if (outcomeIndex == -1) {
                String normalizedTarget = normalizeForCompare(outcome);
                for (int i = 0; i < outcomes.size(); i++) {
                    String candidate = outcomes.get(i);
                    if (!hasText(candidate)) {
                        continue;
                    }
                    String normalizedCandidate = normalizeForCompare(candidate);
                    if (normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate)) {
                        outcomeIndex = i;
                        break;
                    }
                }
            }

            if (outcomeIndex == -1) {
                log.debug("Skip filter: requestedOutcome '{}' not found in outcomes={} for marketId={}", outcome, outcomes, marketId);
                return false; // strictly no YES/NO fallback
            }

            int oppositeIndex = 1 - outcomeIndex;
            String matchedOutcome = outcomes.get(outcomeIndex);
            String oppositeOutcome = outcomes.get(oppositeIndex);
            String oppositeTokenId = clobTokenIds.get(oppositeIndex);

            if (!hasText(oppositeTokenId)) {
                log.debug("Skip filter: oppositeTokenId is blank for marketId={}, outcome={}, oppositeIndex={}",
                        marketId, outcome, oppositeIndex);
                return false;
            }

            BigDecimal oppositeVolume = holdersApiClient.calculateOppositeOutcomeVolume(
                    marketId,
                    oppositeTokenId,
                    signalProperties.getOppositeHoldersLimit()
            );

            BigDecimal threshold = signalProperties.getOppositeHoldersMinAmount();
            boolean filtered = oppositeVolume != null && oppositeVolume.compareTo(threshold) >= 0;

            log.info(
                    "Opposite holders check: marketId={}, requestedOutcome={}, matchedOutcome={}, oppositeOutcome={}, oppositeTokenId={}, volume={}, threshold={}, filtered={}",
                    marketId, outcome, matchedOutcome, oppositeOutcome, oppositeTokenId, oppositeVolume, threshold, filtered
            );

            return filtered;
        } catch (Exception e) {
            log.warn("Opposite holders check failed for marketId={}, outcome={}: {}", marketId, outcome, e.getMessage());
            return false;
        }
    }

    private BigDecimal calculateOppositeVolume(MarketDetailsDto market, String outcome) {
        if (market == null || outcome == null || outcome.isBlank()) {
            return BigDecimal.ZERO;
        }

        List<String> outcomes = market.getOutcomes();
        List<String> clobTokenIds = market.getClobTokenIds();

        if (outcomes == null || outcomes.size() != 2 || clobTokenIds == null || clobTokenIds.size() != 2) {
            return BigDecimal.ZERO;
        }

        int outcomeIndex = -1;
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i) != null && outcomes.get(i).equalsIgnoreCase(outcome)) {
                outcomeIndex = i;
                break;
            }
        }
        if (outcomeIndex == -1) {
            String normalizedTarget = outcome.replaceAll("[^\\p{Alnum}]", "").toLowerCase(Locale.ROOT);
            for (int i = 0; i < outcomes.size(); i++) {
                String candidate = outcomes.get(i);
                if (candidate == null) continue;
                String normalizedCandidate = candidate.replaceAll("[^\\p{Alnum}]", "").toLowerCase(Locale.ROOT);
                if (normalizedCandidate.contains(normalizedTarget) || normalizedTarget.contains(normalizedCandidate)) {
                    outcomeIndex = i;
                    break;
                }
            }
        }
        if (outcomeIndex == -1) return BigDecimal.ZERO;

        int oppositeIndex = 1 - outcomeIndex;
        String oppositeTokenId = clobTokenIds.get(oppositeIndex);
        if (oppositeTokenId == null || oppositeTokenId.isBlank()) return BigDecimal.ZERO;

        try {
            BigDecimal volume = holdersApiClient.calculateOppositeOutcomeVolume(
                    market.getMarketId(),
                    oppositeTokenId,
                    signalProperties.getOppositeHoldersLimit()
            );
            return volume != null ? volume : BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Failed to get opposite volume for market={}, token={}: {}",
                    market.getMarketId(), oppositeTokenId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private String normalizeForCompare(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^\\p{Alnum}]", "").toLowerCase(Locale.ROOT);
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
