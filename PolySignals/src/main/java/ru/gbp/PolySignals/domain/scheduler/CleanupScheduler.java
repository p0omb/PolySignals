package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class CleanupScheduler {

    private final SignalRepository signalRepository;
    private final BetRepository betRepository;

    @Async("cleanupSchedulerExecutor")
    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        try {
            Instant now = Instant.now();
            int signalsDeleted = signalRepository.deleteOldSignals(now.minusSeconds(30L * 24 * 60 * 60));
            int betsDeleted = betRepository.deleteOldBets(now.minusSeconds(90L * 24 * 60 * 60));
            log.info("Cleanup: {} signals, {} bets deleted", signalsDeleted, betsDeleted);
        } catch (Exception e) {
            log.error("Cleanup failed", e);
        }
    }
}

