package ru.gbp.PolySignals.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BetRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired BetRepository betRepository;
    @Autowired WhaleRepository whaleRepository;

    @Test
    void findsBetsByMarketOutcomeAndTimeWindow() {
        Whale whale = whaleRepository.save(whale("u1"));
        betRepository.save(bet(whale, "m1", "yes", Instant.parse("2026-01-01T10:00:00Z")));
        betRepository.save(bet(whale, "m1", "yes", Instant.parse("2026-01-02T10:00:00Z")));

        List<Bet> result = betRepository.findRecentBetsByMarketAndOutcome(
                "m1",
                "yes"
        );

        assertThat(result).hasSize(2);
    }

    @Test
    void rejectsDuplicateExternalBetId() {
        Whale whale = whaleRepository.save(whale("u1"));
        betRepository.saveAndFlush(bet(whale, "m1", "yes", Instant.now()));

        assertThatThrownBy(() -> betRepository.saveAndFlush(bet(whale, "m1", "yes", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
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
        return bet;
    }
}
