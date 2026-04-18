package ru.gbp.PolySignals.domain.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.gbp.PolySignals.domain.service.SignalDetectionService;

import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class SignalDetectionBackupScheduler {

    private final SignalDetectionService signalDetectionService;

    @Async("signalDetectionSchedulerExecutor")
    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        try {
            signalDetectionService.detectAndSendSignals(Duration.ofHours(1), Duration.ofHours(6));
        } catch (Exception e) {
            log.error("Backup failed", e);
        } finally {
            log.info("Backup signal detection completed");
        }
    }
}

