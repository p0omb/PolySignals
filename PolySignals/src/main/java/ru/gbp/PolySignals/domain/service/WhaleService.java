package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WhaleService {

    private final DataApiClient dataApiClient;
    private final WhaleRepository whaleRepository;

    @Transactional
    public void refreshWhales(int limit, SortBy sortBy) {
        List<LeaderboardUserDto> leaderboard = dataApiClient.getTopWhales(limit);
        Instant now = Instant.now();

        for (LeaderboardUserDto dto : leaderboard) {
            Whale whale = whaleRepository.findByUserId(dto.getUserId())
                    .orElseGet(Whale::new);
            whale.setUserId(dto.getUserId());
            whale.setUserName(dto.getUserName());
            whale.setVolume(dto.getVolume());
            whale.setProfit(dto.getProfit());
            whale.setLastSeenAt(now);
            whaleRepository.save(whale);
        }
    }
}