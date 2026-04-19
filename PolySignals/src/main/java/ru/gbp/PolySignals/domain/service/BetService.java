package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.event.NewBetEvent;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class BetService {

    private final BetRepository betRepository;
    private final WhaleRepository whaleRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<Bet> saveNewBets(String userId, List<UserTradeDto> trades) {
        Whale whale = whaleRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Whale not found: " + userId));

        List<Bet> newBets = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return newBets;
        }

        for (UserTradeDto t : trades) {
            if (t == null) {
                log.warn("Skip null trade for user {}", userId);
                continue;
            }

            String marketId = t.getMarketId();
            String normalizedOutcome = normalizeOutcome(t.getOutcome());
            Instant timestamp = t.getTimestamp();

            if (marketId == null || marketId.isBlank() || normalizedOutcome == null || timestamp == null) {
                log.warn("Skip invalid trade for user {}: marketId='{}', outcome='{}', timestamp={}",
                        userId, marketId, normalizedOutcome, timestamp);
                continue;
            }

            String uk = buildUniqueKey(whale.getUserId(), marketId, normalizedOutcome, timestamp);
            if (betRepository.existsByUniqueKey(uk)) {
                continue;
            }

            try {
                Bet bet = new Bet();
                bet.setWhale(whale);
                bet.setMarketId(marketId);
                bet.setOutcome(normalizedOutcome);
                bet.setAmount(t.getSize());
                bet.setTransactionHash(t.getTransactionHash());
                bet.setTransactionTime(timestamp);
                bet.setCreatedAt(Instant.now());
                bet.setUniqueKey(uk);

                Bet savedBet = betRepository.save(bet);
                newBets.add(savedBet);

                log.info("Bet saved, id={}, market={}, outcome={}, whaleId={}",
                        savedBet.getId(),
                        savedBet.getMarketId(),
                        savedBet.getOutcome(),
                        savedBet.getWhale() != null ? savedBet.getWhale().getId() : null);
                log.info("Publishing NewBetEvent for bet id={}", savedBet.getId());

                eventPublisher.publishEvent(new NewBetEvent(savedBet));
            } catch (Exception ex) {
                log.error("Failed to save/publish bet for userId={}, marketId={}, outcome={}, timestamp={}",
                        userId, marketId, normalizedOutcome, timestamp, ex);
                throw ex;
            }
        }

        return newBets;
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) {
            return null;
        }
        String trimmed = outcome.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String buildUniqueKey(String userId, String marketId, String outcome, Instant time) {
        return userId + "|" + marketId + "|" + outcome + "|" + time.toEpochMilli();
    }
}
