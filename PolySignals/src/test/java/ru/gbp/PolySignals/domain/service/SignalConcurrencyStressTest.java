package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.SignalRepository;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;
import ru.gbp.PolySignals.client.telegram.NotificationService;
import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class SignalConcurrencyStressTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private BetRepository betRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private WhaleRepository whaleRepository;

    @Autowired
    private SignalDetectionService signalDetectionService;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void createsSingleSignalUnderParallelCalls() throws Exception {
        Whale w1 = whale("u1");
        Whale w2 = whale("u2");
        whaleRepository.saveAll(List.of(w1, w2));

        betRepository.save(bet(w1, "m1", "yes", Instant.parse("2026-01-01T10:00:00Z")));
        Bet newBet = betRepository.save(bet(w2, "m1", "yes", Instant.parse("2026-01-01T11:00:00Z")));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> signalDetectionService.checkSignalForNewBet(
                    newBet,
                    Duration.ofDays(4),
                    Duration.ofHours(6)
            ));
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(signalRepository.findAll()).hasSize(1);
    }

    private Whale whale(String userId) {
        Whale whale = new Whale();
        whale.setUserId(userId);
        whale.setUserName("whale-" + userId);
        whale.setVolume(new BigDecimal("100"));
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
        return bet;
    }
}
