package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    BetRepository betRepository;
    @Mock
    SignalRepository signalRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    GammaApiClient gammaApiClient;
    @Mock
    HoldersApiClient holdersApiClient;
    @Mock
    SignalProperties signalProperties;

    @InjectMocks
    SignalDetectionService signalDetectionService;

    @BeforeEach
    void setUp() {
        lenient().when(signalRepository.saveAndFlush(any(Signal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(betRepository.findDistinctWhaleNamesByMarketOutcomeAndTimeRange(anyString(), anyString(), any(), any()))
                .thenReturn(List.of("whale1", "whale2"));
        lenient().when(gammaApiClient.getMarket(anyString())).thenReturn(null);
        lenient().when(holdersApiClient.calculateOppositeOutcomeVolume(anyString(), anyString(), anyInt()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(signalProperties.getFilterOppositeHolders()).thenReturn(false);
        lenient().when(signalProperties.getOppositeHoldersMinAmount()).thenReturn(new BigDecimal("1000"));
        lenient().when(signalProperties.getOppositeHoldersLimit()).thenReturn(20);
        lenient().when(signalProperties.getMinTotalAmount()).thenReturn(BigDecimal.ZERO);
        lenient().when(signalProperties.getExcludedTitlePatterns()).thenReturn(List.of());
    }

    @Test
    void createsSignalWhenSecondWhaleAppearsInWindow() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "yes", 2L, new BigDecimal("30"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));

        ArgumentCaptor<Signal> signalCaptor = ArgumentCaptor.forClass(Signal.class);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(signalCaptor.capture());
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));

        Signal savedSignal = signalCaptor.getValue();
        assertThat(savedSignal.getMarketId()).isEqualTo("m1");
        assertThat(savedSignal.getOutcome()).isEqualTo("YES");
        assertThat(savedSignal.getSignalKey()).isNotBlank();
        assertThat(savedSignal.getFirstBetAt()).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
        assertThat(savedSignal.getLastBetAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void doesNotCreateSignalWhenCooldownActive() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "yes", 2L, new BigDecimal("30"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        when(signalRepository.saveAndFlush(any(Signal.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint signal_key"));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void ignoresBetBelowMinBetSize() {
        Bet newBet = bet("m1", "yes", "whale2", "1", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "yes", 1L, new BigDecimal("1"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(betRepository).findMarketOutcomeSignalStats(anyString(), anyString(), any(), any());
        verify(signalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    // НОВЫЕ ТЕСТЫ ДЛЯ ФИЛЬТРАЦИИ ПО ПРОТИВОПОЛОЖНЫМ ХОЛДЕРАМ

    @Test
    void filtersBinaryMarketWhenOppositeHoldersExceedThreshold() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "YES", 2L, new BigDecimal("2000"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        // Рынок бинарный: clobTokenIds содержит 2 токена
        MarketDetailsDto market = new MarketDetailsDto();
        market.setClobTokenIds(List.of("token-yes", "token-no"));
        when(gammaApiClient.getMarket("m1")).thenReturn(market);
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
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        MarketDetailsDto market = new MarketDetailsDto();
        market.setClobTokenIds(List.of("token-yes", "token-no"));
        when(gammaApiClient.getMarket("m1")).thenReturn(market);
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
        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "YES", 2L, new BigDecimal("2000"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        // Рынок не бинарный: clobTokenIds содержит 3 токена
        MarketDetailsDto market = new MarketDetailsDto();
        market.setClobTokenIds(List.of("token-a", "token-b", "token-c"));
        when(gammaApiClient.getMarket("m1")).thenReturn(market);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verifyNoInteractions(holdersApiClient);
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));
    }

    @Test
    void doesNotFilterWhenClobTokenIdsMissing() {
        Bet newBet = bet("m1", "YES", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(signalProperties.getFilterOppositeHolders()).thenReturn(true);
        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1", "YES", 2L, new BigDecimal("2000"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        MarketDetailsDto market = new MarketDetailsDto();
        market.setClobTokenIds(null);
        when(gammaApiClient.getMarket("m1")).thenReturn(market);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(any(Signal.class));
        verifyNoInteractions(holdersApiClient);
        verify(eventPublisher).publishEvent(any(SignalCreatedEvent.class));
    }

    // Вспомогательные методы

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

    private static final class MarketOutcomeSignalStatsProjectionStub implements MarketOutcomeSignalProjection {
        private final String marketId;
        private final String outcome;
        private final Long whalesCount;
        private final BigDecimal totalAmount;
        private final Instant firstBetAt;
        private final Instant lastBetAt;

        private MarketOutcomeSignalStatsProjectionStub(
                String marketId,
                String outcome,
                Long whalesCount,
                BigDecimal totalAmount,
                Instant firstBetAt,
                Instant lastBetAt
        ) {
            this.marketId = marketId;
            this.outcome = outcome;
            this.whalesCount = whalesCount;
            this.totalAmount = totalAmount;
            this.firstBetAt = firstBetAt;
            this.lastBetAt = lastBetAt;
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
    }
}
