package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.SignalRepository;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;
import ru.gbp.PolySignals.client.telegram.NotificationService;
import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class SignalDetectionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired BetRepository betRepository;
    @Autowired WhaleRepository whaleRepository;
    @Autowired SignalRepository signalRepository;
    @Autowired SignalDetectionService signalDetectionService;

    @MockitoBean NotificationService notificationService;

    @Test
    void createsSignalForTwoWhales() {
        Whale w1 = whaleRepository.save(whale("u1"));
        Whale w2 = whaleRepository.save(whale("u2"));

        betRepository.save(bet(w1, "m1", "yes", Instant.parse("2026-01-01T10:00:00Z")));
        Bet newBet = betRepository.save(bet(w2, "m1", "yes", Instant.parse("2026-01-01T11:00:00Z")));

        signalDetectionService.checkSignalForNewBet(newBet, Duration.ofDays(4), Duration.ofHours(6));

        assertThat(signalRepository.findAll())
                .singleElement()
                .satisfies(signal -> {
                    assertThat(signal.getMarketId()).isEqualTo("m1");
                    assertThat(signal.getOutcome()).isEqualTo("YES");  // нормализуется в верхний регистр
                    assertThat(signal.getSignalKey()).isNotBlank();
                    // Проверяем, что whaleWeight это SUM(w1.weight + w2.weight)
                    assertThat(signal.getWhaleWeight()).isEqualTo(1.0);  // 0.5 + 0.5 (default weights)
                });

        verify(notificationService).sendSignalNotification(any());
    }

    private Whale whale(String userId) {
        Whale whale = new Whale();
        whale.setUserId(userId);
        whale.setUserName("w-" + userId);
        whale.setVolume(BigDecimal.TEN);
        whale.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        return whale;
    }

    private Bet bet(Whale whale, String marketId, String outcome, Instant time) {
        Bet bet = new Bet();
        bet.setWhale(whale);
        bet.setMarketId(marketId);
        bet.setOutcome(outcome);
        bet.setAmount(new BigDecimal("20"));
        bet.setTransactionTime(time);
        bet.setCreatedAt(time);

        return bet;
    }
}
