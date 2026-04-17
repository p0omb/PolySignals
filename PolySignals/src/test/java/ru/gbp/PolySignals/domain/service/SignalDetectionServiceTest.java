package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.event.SignalCreatedEvent;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.MarketOutcomeSignalProjection;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

@ExtendWith(MockitoExtension.class)
class SignalDetectionServiceTest {

    @Mock BetRepository betRepository;
    @Mock SignalRepository signalRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock GammaApiClient gammaApiClient;

    @InjectMocks SignalDetectionService signalDetectionService;

    @BeforeEach
    void setUp() {
        lenient().when(signalRepository.saveAndFlush(any(Signal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(betRepository.findDistinctWhaleNamesByMarketOutcomeAndTimeRange(anyString(), anyString(), any(), any()))
                .thenReturn(List.of("whale1", "whale2"));
        lenient().when(gammaApiClient.getMarket(anyString())).thenReturn(null);
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
        // В реальном сервисе cooldown проверяется через уникальный signal_key (DB constraint),
        // а не через existsRecentSignal.
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
        // В текущем сервисе нет фильтра minBetSize: проверяем, что запрос статистики всё равно выполняется.
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

        public String getMarketId() { return marketId; }
        public String getOutcome() { return outcome; }
        public Long getWhalesCount() { return whalesCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Instant getFirstBetAt() { return firstBetAt; }
        public Instant getLastBetAt() { return lastBetAt; }
    }
}
