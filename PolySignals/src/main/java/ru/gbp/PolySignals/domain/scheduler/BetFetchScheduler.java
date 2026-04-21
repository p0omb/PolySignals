package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.ErrorCategory;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler.PolymarketApiException;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;
import ru.gbp.PolySignals.domain.service.BetService;
import ru.gbp.PolySignals.monitoring.SchedulerHealthIndicator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BetFetchScheduler {

    private static final int TOP_WHALES = 100;
    private static final int TRADES_LIMIT = 200;
    private static final Duration FETCH_WINDOW = Duration.ofDays(4);

    private final WhaleRepository whaleRepository;
    private final DataApiClient dataApiClient;
    private final BetService betService;
    private final SchedulerHealthIndicator schedulerHealthIndicator;

    @Value("${app.polymarket.request-delay-ms:75}")
    private long requestDelayMs;

    @Async("betFetchSchedulerExecutor")
    @Scheduled(fixedDelay = 180_000)
    public void run() {
        List<Whale> whales = whaleRepository.findActiveWhales();

        int checked = 0;
        int rateLimited = 0;
        int failed = 0;
        int totalNewBets = 0;

        Instant after = Instant.now().minus(FETCH_WINDOW);

        for (Whale whale : whales) {
            checked++;
            String userId = whale.getUserId();
            try {
                List<UserTradeDto> trades = dataApiClient.getUserTradesSince(userId, after, TRADES_LIMIT);
                List<Bet> saved = betService.saveNewBets(userId, trades);
                totalNewBets += saved.size();
                log.debug("Whale {}: {} new bets", userId, saved.size());
            } catch (PolymarketApiException e) {
                if (e.getCategory() == ErrorCategory.RATE_LIMIT) {
                    rateLimited++;
                    log.warn("Rate limit for whale {}: {}", userId, e.getMessage());
                } else {
                    failed++;
                    log.error("API error for whale {}: {}", userId, e.getMessage(), e);
                }
            } catch (Exception e) {
                failed++;
                log.error("Bet fetch failed for whale {}", userId, e);
            }

            if (!applyRequestDelay()) {
                break;
            }
        }

        log.info(
                "Bet fetch: checked {}, new bets {}, rate-limited {}, failed {}",
                checked, totalNewBets, rateLimited, failed
        );
        schedulerHealthIndicator.updateLastRun();
    }

    private boolean applyRequestDelay() {
        if (requestDelayMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(requestDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("BetFetchScheduler interrupted during request delay");
            return false;
        }
    }
}
