package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import ru.gbp.PolySignals.client.polymarket.GammaApiClient;
import ru.gbp.PolySignals.client.polymarket.HoldersApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
import ru.gbp.PolySignals.config.properties.SignalProperties;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.event.SignalCreatedEvent;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.MarketOutcomeSignalProjection;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

@ExtendWith(MockitoExtension.class)
class SignalDetectionServiceTest {

    @Mock
    private BetRepository betRepository;

    @Mock
    private SignalRepository signalRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GammaApiClient gammaApiClient;

    @Mock
    private HoldersApiClient holdersApiClient;

    @Mock
    private SignalProperties signalProperties;

    @InjectMocks
    private SignalDetectionService signalDetectionService;

    @BeforeEach
    void setUp() {
        lenient().when(signalRepository.saveAndFlush(any(Signal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(signalRepository.findBySignalKey(anyString()))
                .thenReturn(Optional.empty());

        lenient().when(betRepository.findDistinctWhaleNamesByMarketOutcomeAndTimeRange(anyString(), anyString(), any(), any()))
                .thenReturn(List.of("whale1", "whale2"));

        lenient().when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1",
                        "YES",
                        2L,
                        new BigDecimal("30"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        2.0   // whaleWeight: SUM всех весов китов (0.7 + 1.3 = 2.0)
                ));

        // Сервис использует отдельный метод для суммы весов уникальных китов
        lenient().when(betRepository.getTotalWhaleWeight(anyString(), anyString(), any(), any()))
                .thenReturn(2.0);

        lenient().when(gammaApiClient.getMarket(anyString()))
                .thenAnswer(invocation -> validBinaryMarket(invocation.getArgument(0, String.class)));

        lenient().when(holdersApiClient.calculateOppositeOutcomeVolume(anyString(), anyString(), anyInt()))
                .thenReturn(BigDecimal.ZERO);

        lenient().when(signalProperties.getMinTotalAmount()).thenReturn(BigDecimal.ZERO);
        lenient().when(signalProperties.getFilterOppositeHolders()).thenReturn(false);
        lenient().when(signalProperties.getExcludedTitlePatterns()).thenReturn(null);
        lenient().when(signalProperties.getOppositeHoldersMinAmount()).thenReturn(new BigDecimal("1000"));
        lenient().when(signalProperties.getOppositeHoldersLimit()).thenReturn(20);

        ReflectionTestUtils.setField(signalDetectionService, "maxBetsPerDay", 1000);
        ReflectionTestUtils.setField(signalDetectionService, "minIntervalMinutes", 0);
        ReflectionTestUtils.setField(signalDetectionService, "topPercent", 100.0);
        ReflectionTestUtils.setField(signalDetectionService, "freshnessHalfLife", 1_000_000L);
        ReflectionTestUtils.setField(signalDetectionService, "whaleWeightFilterEnabled", true);
    }

    @Test
    void createsSignalWhenSecondWhaleAppearsInWindow() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        ArgumentCaptor<Signal> signalCaptor = ArgumentCaptor.forClass(Signal.class);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(signalCaptor.capture());
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));

        Signal savedSignal = signalCaptor.getValue();
        assertThat(savedSignal.getMarketId()).isEqualTo("m1");
        assertThat(savedSignal.getOutcome()).isEqualTo("YES");
        assertThat(savedSignal.getWhalesCount()).isEqualTo(2);
        assertThat(savedSignal.getTotalAmount()).isEqualByComparingTo("30");
        assertThat(savedSignal.getSignalKey()).isNotBlank();
        assertThat(savedSignal.getFirstBetAt()).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
        assertThat(savedSignal.getLastBetAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(savedSignal.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(savedSignal.getScore()).isNotNull();
        assertThat(savedSignal.getWhaleLabels()).isNotNull();
        assertThat(savedSignal.getWhaleWeight()).isEqualTo(2.0);
    }

    @Test
    void doesNotCreateSignalWhenCooldownActive() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        AtomicBoolean saved = new AtomicBoolean(false);
        when(signalRepository.findBySignalKey(anyString()))
                .thenAnswer(invocation -> saved.get() ? Optional.of(new Signal()) : Optional.empty());
        when(signalRepository.saveAndFlush(any(Signal.class)))
                .thenAnswer(invocation -> {
                    saved.set(true);
                    return invocation.getArgument(0);
                });

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));
        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, times(1)).saveAndFlush(any(Signal.class));
        verify(eventPublisher, times(1)).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void ignoresBetBelowMinBetSize() {
        Bet newBet = bet("m1", "yes", "whale2", "1", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "yes", 1L, new BigDecimal("1"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        0.5   // низкий вес не влияет, т.к. whalesCount < MIN_WHALES
                ));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(betRepository).findMarketOutcomeSignalStats(anyString(), anyString(), any(), any());
        verify(signalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void filtersBinaryMarketWhenOppositeHoldersExceedThreshold() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "YES", 2L, new BigDecimal("2000"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        2.0
                ));
        MarketDetailsDto market = new MarketDetailsDto();
        market.setOutcomesRaw("[\"YES\",\"NO\"]");
        market.setClobTokenIdsRaw("[\"token-yes\",\"token-no\"]");
        doReturn(market).when(gammaApiClient).getMarket("m1");
        when(holdersApiClient.calculateOppositeOutcomeVolume("m1", "token-no", 20))
                .thenReturn(new BigDecimal("1500"));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void doesNotFilterBinaryMarketWhenOppositeHoldersBelowThreshold() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "YES", 2L, new BigDecimal("2000"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        2.0
                ));
        MarketDetailsDto market = new MarketDetailsDto();
        market.setOutcomesRaw("[\"YES\",\"NO\"]");
        market.setClobTokenIdsRaw("[\"token-yes\",\"token-no\"]");
        market.setVolumeClobRaw("100000");
        market.setLiquidityClobRaw("50000");
        doReturn(market).when(gammaApiClient).getMarket("m1");
        when(holdersApiClient.calculateOppositeOutcomeVolume("m1", "token-no", 20))
                .thenReturn(new BigDecimal("10"));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void doesNotFilterNonBinaryMarket() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        doReturn(nonBinaryMarket("m1")).when(gammaApiClient).getMarket("m1");

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verifyNoInteractions(holdersApiClient);
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void doesNotFilterWhenClobTokenIdsMissing() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        doReturn(marketWithoutClobTokenIds("m1")).when(gammaApiClient).getMarket("m1");

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verifyNoInteractions(holdersApiClient);
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void signalBlockedByDailyLimit() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        ReflectionTestUtils.setField(signalDetectionService, "maxBetsPerDay", 0);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, never()).saveAndFlush(any(Signal.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void signalBlockedByMinInterval() {
        ReflectionTestUtils.setField(signalDetectionService, "minIntervalMinutes", 60);

        Bet firstBet = bet("m1", "yes", "whale1", "20", Instant.parse("2026-01-01T10:00:00Z"));
        Bet secondBet = bet("m2", "yes", "whale2", "20", Instant.parse("2026-01-01T10:10:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(
                        new MarketOutcomeSignalStatsProjectionStub(
                                "m1",
                                "YES",
                                2L,
                                new BigDecimal("30"),
                                Instant.parse("2026-01-01T09:00:00Z"),
                                Instant.parse("2026-01-01T10:00:00Z"),
                                2.0
                        ),
                        new MarketOutcomeSignalStatsProjectionStub(
                                "m2",
                                "YES",
                                2L,
                                new BigDecimal("30"),
                                Instant.parse("2026-01-01T10:00:00Z"),
                                Instant.parse("2026-01-01T10:10:00Z"),
                                2.0
                        )
                );

        doReturn(validBinaryMarket("m1")).when(gammaApiClient).getMarket("m1");
        doReturn(validBinaryMarket("m2")).when(gammaApiClient).getMarket("m2");

        signalDetectionService.checkSignalForNewBet(firstBet, Duration.ofDays(4), Duration.ofHours(6));
        signalDetectionService.checkSignalForNewBet(secondBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, times(1)).saveAndFlush(any(Signal.class));
        verify(eventPublisher, times(1)).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void signalFilteredByDynamicThreshold() {
        ReflectionTestUtils.setField(signalDetectionService, "topPercent", 0.0);

        Bet firstBet = bet("m1", "yes", "whale1", "1000", Instant.parse("2026-01-01T10:00:00Z"));
        Bet secondBet = bet("m2", "yes", "whale2", "1", Instant.parse("2026-01-01T10:05:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(
                        new MarketOutcomeSignalStatsProjectionStub(
                                "m1",
                                "YES",
                                2L,
                                new BigDecimal("1000"),
                                Instant.parse("2026-01-01T09:00:00Z"),
                                Instant.parse("2026-01-01T10:00:00Z"),
                                2.0
                        ),
                        new MarketOutcomeSignalStatsProjectionStub(
                                "m2",
                                "YES",
                                2L,
                                new BigDecimal("1"),
                                Instant.parse("2026-01-01T10:00:00Z"),
                                Instant.parse("2026-01-01T10:05:00Z"),
                                2.0
                        )
                );

        doReturn(validBinaryMarket("m1")).when(gammaApiClient).getMarket("m1");
        doReturn(lowScoreMarket("m2")).when(gammaApiClient).getMarket("m2");

        signalDetectionService.checkSignalForNewBet(firstBet, Duration.ofDays(4), Duration.ofHours(6));
        signalDetectionService.checkSignalForNewBet(secondBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, times(1)).saveAndFlush(any(Signal.class));
        verify(eventPublisher, times(1)).publishEvent(any(SignalCreatedEvent.class));
    }

    private Bet bet(String marketId, String outcome, String whaleId, String amount, Instant time) {
        Bet bet = new Bet();
        bet.setMarketId(marketId);
        bet.setOutcome(outcome);
        bet.setAmount(new BigDecimal(amount));
        bet.setTransactionTime(time);

        Whale whale = new Whale();
        whale.setUserId(whaleId);
        whale.setUserName(whaleId);
        whale.setVolume(new BigDecimal("100"));
        whale.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        bet.setWhale(whale);

        return bet;
    }

    private MarketDetailsDto validBinaryMarket(String marketId) {
        MarketDetailsDto market = new MarketDetailsDto();
        market.setQuestion("Question " + marketId);
        market.setSlug("slug-" + marketId);
        market.setOutcomesRaw("[\"YES\",\"NO\"]");
        market.setClobTokenIdsRaw("[\"token-yes\",\"token-no\"]");
        market.setVolumeClobRaw("100000");
        market.setLiquidityClobRaw("50000");
        return market;
    }

    private MarketDetailsDto lowScoreMarket(String marketId) {
        MarketDetailsDto market = new MarketDetailsDto();
        market.setQuestion("Question " + marketId);
        market.setSlug("slug-" + marketId);
        market.setOutcomesRaw("[\"YES\",\"NO\"]");
        market.setClobTokenIdsRaw("[\"token-yes\",\"token-no\"]");
        market.setVolumeClobRaw(null);
        market.setLiquidityClobRaw(null);
        return market;
    }

    private MarketDetailsDto nonBinaryMarket(String marketId) {
        MarketDetailsDto market = new MarketDetailsDto();
        market.setQuestion("Question " + marketId);
        market.setSlug("slug-" + marketId);
        market.setOutcomesRaw("[\"Team A\",\"Team B\",\"Team C\"]");
        market.setClobTokenIdsRaw("[\"token-a\",\"token-b\",\"token-c\"]");
        market.setVolumeClobRaw("100000");
        market.setLiquidityClobRaw("50000");
        return market;
    }

    private MarketDetailsDto marketWithoutClobTokenIds(String marketId) {
        MarketDetailsDto market = new MarketDetailsDto();
        market.setQuestion("Question " + marketId);
        market.setSlug("slug-" + marketId);
        market.setOutcomesRaw("[\"YES\",\"NO\"]");
        market.setClobTokenIdsRaw(null);
        market.setVolumeClobRaw("100000");
        market.setLiquidityClobRaw("50000");
        return market;
    }

    private static final class MarketOutcomeSignalStatsProjectionStub implements MarketOutcomeSignalProjection {
        private final String marketId;
        private final String outcome;
        private final Long whalesCount;
        private final BigDecimal totalAmount;
        private final Instant firstBetAt;
        private final Instant lastBetAt;
        private final Double whaleWeight;

        private MarketOutcomeSignalStatsProjectionStub(
                String marketId,
                String outcome,
                Long whalesCount,
                BigDecimal totalAmount,
                Instant firstBetAt,
                Instant lastBetAt,
                Double whaleWeight) {
            this.marketId = marketId;
            this.outcome = outcome;
            this.whalesCount = whalesCount;
            this.totalAmount = totalAmount;
            this.firstBetAt = firstBetAt;
            this.lastBetAt = lastBetAt;
            this.whaleWeight = whaleWeight;
        }

        @Override
        public String getMarketId() {
            return marketId;
        }

        @Override
        public String getOutcome() {
            return outcome;
        }

        @Override
        public Long getWhalesCount() {
            return whalesCount;
        }

        @Override
        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        @Override
        public Instant getFirstBetAt() {
            return firstBetAt;
        }

        @Override
        public Instant getLastBetAt() {
            return lastBetAt;
        }

        @Override
        public Double getWhaleWeight() {
            return whaleWeight;
        }
    }
}