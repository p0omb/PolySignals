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


        if (leaderboard == null || leaderboard.isEmpty()) {
            return 0;
        }

        for (LeaderboardUserDto dto : leaderboard) {
            if (dto == null || dto.getUserId() == null || dto.getUserId().isBlank()) {
                continue;
            }

            Whale whale = whaleRepository.findByUserId(dto.getUserId()).orElse(null);

            if (whale != null) {
                // Если кит помечен как игнорируемый – пропускаем обновление
                if (Boolean.TRUE.equals(whale.getIgnored())) {
                    continue;
                }
                whale.setUserName(dto.getUserName());
                whale.setVolume(dto.getVolume());
                whale.setProfit(dto.getProfit());
                whale.setLastSeenAt(now);
                whaleRepository.save(whale);
                updated++;
            } else {
                // Новый кит – добавляем с ignored = false
                whale = new Whale();
                whale.setUserId(dto.getUserId());
                whale.setUserName(dto.getUserName());
                whale.setVolume(dto.getVolume());
                whale.setProfit(dto.getProfit());
                whale.setLastSeenAt(now);
                whale.setIgnored(false);
                whaleRepository.save(whale);
                updated++;
            }
        }
        return updated;
    }

    @Transactional
    public void refreshWhales(int limit) {
        List<LeaderboardUserDto> leaderboard = dataApiClient.getTopWhales(limit);
        updateWhales(leaderboard);
    }

    @Transactional
    public Whale addWhale(String address, String name) {
        String normalizedAddress = normalizeAddress(address);
        if (whaleRepository.findByUserId(normalizedAddress).isPresent()) {
            throw new IllegalStateException("Whale already exists: " + normalizedAddress);
        }

        Whale whale = new Whale();
        whale.setUserId(normalizedAddress);
        whale.setUserName(normalizeName(name));
        whale.setVolume(java.math.BigDecimal.ZERO);
        whale.setProfit(java.math.BigDecimal.ZERO);
        whale.setLastSeenAt(Instant.now());
        whale.setIgnored(false);
        whale.setActive(true);
        whale.setWeight(0.5);
        return whaleRepository.save(whale);
    }

    @Transactional
    public boolean removeWhale(String address) {
        String normalizedAddress = normalizeAddress(address);
        return whaleRepository.findByUserId(normalizedAddress)
                .map(whale -> {
                    whaleRepository.delete(whale);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<Whale> getAllWhales() {
        return whaleRepository.findAll();
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Whale address must not be blank");
        }
        return address.trim();
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
