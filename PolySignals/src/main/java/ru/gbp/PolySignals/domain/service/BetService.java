package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BetService {

    private final BetRepository betRepository;
    private final WhaleRepository whaleRepository;

    @Transactional
    public List<Bet> saveNewBets(String userId, List<UserTradeDto> trades) {
        Whale whale = whaleRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Whale not found: " + userId));

        List<Bet> newBets = new ArrayList<>();

        for (UserTradeDto t : trades) {
            String uk = buildUniqueKey(whale.getUserId(), t.getMarketId(), t.getOutcome(), t.getTimestamp());

            if (betRepository.existsByUniqueKey(uk)) {
                continue;
            }

            Bet bet = new Bet();
            bet.setWhale(whale);
            bet.setMarketId(t.getMarketId());
            bet.setOutcome(t.getOutcome());
            bet.setAmount(t.getSize());
            bet.setTransactionHash(t.getTransactionHash());
            bet.setTransactionTime(t.getTimestamp());
            bet.setCreatedAt(Instant.now());
            bet.setUniqueKey(uk);

            betRepository.save(bet);
            newBets.add(bet);
        }

        return newBets;
    }

    private String buildUniqueKey(String userId, String marketId, String outcome, Instant time) {
        return userId + "|" + marketId + "|" + outcome + "|" + time.toEpochMilli();
    }
}
