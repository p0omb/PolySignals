package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WhaleService {

    private final DataApiClient dataApiClient;
    private final WhaleRepository whaleRepository;

    @Transactional
    public int updateWhales(List<LeaderboardUserDto> leaderboard) {
        Instant now = Instant.now();
        int updated = 0;

        for (LeaderboardUserDto dto : leaderboard) {
            Whale whale = whaleRepository.findByUserId(dto.getUserId()).orElseGet(Whale::new);
            whale.setUserId(dto.getUserId());
            whale.setUserName(dto.getUserName());
            whale.setVolume(dto.getVolume());
            whale.setProfit(dto.getProfit());
            whale.setLastSeenAt(now);
            whaleRepository.save(whale);
            updated++;
        }
        return updated;
    }

    @Transactional
    public void refreshWhales(int limit) {
        List<LeaderboardUserDto> leaderboard = dataApiClient.getTopWhales(limit);
        updateWhales(leaderboard);
    }
}