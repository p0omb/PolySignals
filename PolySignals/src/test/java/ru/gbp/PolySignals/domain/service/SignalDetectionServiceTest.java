package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.gbp.PolySignals.config.properties.SignalProperties;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.MarketOutcomeSignalProjection;
import ru.gbp.PolySignals.domain.repository.SignalRepository;
import ru.gbp.PolySignals.client.telegram.NotificationService;
import ru.gbp.PolySignals.client.telegram.SignalNotificationPayload;

@ExtendWith(MockitoExtension.class)
class SignalDetectionServiceTest {

    @Mock BetRepository betRepository;
    @Mock SignalRepository signalRepository;
    @Mock NotificationService notificationService;
    @Mock SignalProperties signalProperties;

    @InjectMocks SignalDetectionService signalDetectionService;

//    @BeforeEach
//    void setUp() {
//        when(signalProperties.getMinBetSize()).thenReturn(new BigDecimal("10"));
//    }

    @Test
    void createsSignalWhenSecondWhaleAppearsInWindow() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1",
                        "yes",
                        2L,
                        new BigDecimal("30"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        when(signalRepository.existsRecentSignal(anyString(), anyString(), any())).thenReturn(false);
        when(signalRepository.saveAndFlush(any(Signal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Signal> signalCaptor = ArgumentCaptor.forClass(Signal.class);
        ArgumentCaptor<SignalNotificationPayload> payloadCaptor = ArgumentCaptor.forClass(SignalNotificationPayload.class);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository).saveAndFlush(signalCaptor.capture());
        verify(notificationService).sendSignalNotification(payloadCaptor.capture());

        Signal savedSignal = signalCaptor.getValue();
        assertThat(savedSignal.getMarketId()).isEqualTo("m1");
        assertThat(savedSignal.getOutcome()).isEqualTo("yes");
        assertThat(savedSignal.getSignalKey()).isNotBlank();
        assertThat(savedSignal.getFirstBetAt()).isEqualTo(Instant.parse("2026-01-01T09:00:00Z"));
        assertThat(savedSignal.getLastBetAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));

        SignalNotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.marketId()).isEqualTo("m1");
        assertThat(payload.outcome()).isEqualTo("yes");
        assertThat(payload.whalesCount()).isEqualTo(2);
    }

    @Test
    void doesNotCreateSignalWhenCooldownActive() {
        Bet newBet = bet("m1", "yes", "whale2", "20", Instant.parse("2026-01-01T10:00:00Z"));

        when(betRepository.findMarketOutcomeSignalStats(anyString(), anyString(), any(), any()))
                .thenReturn(new MarketOutcomeSignalStatsProjectionStub(
                        "m1",
                        "yes",
                        2L,
                        new BigDecimal("30"),
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T10:00:00Z")
                ));
        when(signalRepository.existsRecentSignal(anyString(), anyString(), any())).thenReturn(true);

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verify(signalRepository, never()).saveAndFlush(any());
        verify(notificationService, never()).sendSignalNotification(any());
    }

    @Test
    void ignoresBetBelowMinBetSize() {
        Bet newBet = bet("m1", "yes", "whale2", "1", Instant.parse("2026-01-01T10:00:00Z"));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        verifyNoInteractions(betRepository, signalRepository, notificationService);
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
