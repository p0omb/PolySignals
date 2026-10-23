package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.PolymarketApiException;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.ErrorCategory;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.domain.service.WhaleService;
import ru.gbp.PolySignals.monitoring.SchedulerHealthIndicator;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.scheduling.whale-update.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class WhaleUpdateScheduler {

    private static final int LIMIT = 100;

    private final DataApiClient dataApiClient;
    private final WhaleService whaleService;
    private final SchedulerHealthIndicator schedulerHealthIndicator;

    @Async("whaleUpdateSchedulerExecutor")
    @Scheduled(fixedDelay = 1_800_000)
    public void run() {
        try {
            List<LeaderboardUserDto> leaderboard = dataApiClient.getTopWhales(LIMIT);
            int updated = whaleService.updateWhales(leaderboard);
            log.info("Updated {} whales", updated);
            schedulerHealthIndicator.updateLastRun();
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
