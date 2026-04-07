package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.repository.SignalRepository;
import ru.gbp.PolySignals.domain.service.MarketCacheService;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarketCacheRefreshScheduler {

    private final SignalRepository signalRepository;
    private final MarketCacheService marketCacheService;

    @Async("marketCacheSchedulerExecutor")
    @Scheduled(fixedDelay = 3_600_000)
    public void run() {
        List<Signal> recent = signalRepository.findRecentSignals(Duration.ofHours(24));
        int refreshed = 0;

        for (Signal s : recent) {
            String marketId = s.getMarketId();
            try {
                marketCacheService.refreshMarket(marketId);
                refreshed++;
            } catch (Exception e) {
                log.debug("Skip market {}: {}", marketId, e.getMessage());
            }
        }

        log.info("Refreshed {} market caches", refreshed);
    }
}

