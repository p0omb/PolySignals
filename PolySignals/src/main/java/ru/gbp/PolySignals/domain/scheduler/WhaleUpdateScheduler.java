package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.PolymarketApiException;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.ErrorCategory;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.domain.service.WhaleService;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhaleUpdateScheduler {

    private static final int LIMIT = 100;

    private final DataApiClient dataApiClient;
    private final WhaleService whaleService;

    @Async("whaleUpdateSchedulerExecutor")
    @Scheduled(fixedDelay = 1_800_000)
    public void run() {
        try {
            List<LeaderboardUserDto> leaderboard = dataApiClient.getTopWhales(LIMIT);
            int updated = whaleService.updateWhales(leaderboard);
            log.info("Updated {} whales", updated);
        } catch (PolymarketApiException e) {
            if (e.getCategory() == ErrorCategory.RATE_LIMIT) {
                log.warn("Rate limit on whale update: {}", e.getMessage());
                return;
            }
            log.error("Whale update failed: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Whale update unexpected error", e);
        }
    }
}

