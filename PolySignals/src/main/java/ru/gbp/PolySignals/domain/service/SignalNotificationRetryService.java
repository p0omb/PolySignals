package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.gbp.PolySignals.client.telegram.NotificationService;
import ru.gbp.PolySignals.client.telegram.SignalNotificationPayload;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.event.SignalCreatedEvent;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalNotificationRetryService {

    private final SignalRepository signalRepository;
    private final NotificationService notificationService;
    private final RetryTemplate signalNotificationRetryTemplate;

    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Value("${app.signal.retry-unsent-enabled:false}")
    private boolean retryUnsentEnabled;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignalCreated(SignalCreatedEvent event) {
        if (event == null || event.signalId() == null) return;
        trySend(event.signalId());
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.notification.retry.delay:60000}")
    public void retryUnsentSignals() {
        if (!retryUnsentEnabled) {
            log.info("retryUnsentSignals skipped: disabled by config");
            return;
        }
        int batchSize = 100;
        List<Long> unsentIds = signalRepository.findUnsentSignalIds(PageRequest.of(0, batchSize));
        for (Long id : unsentIds) {
            trySend(id);
        }
    }

    private void trySend(Long signalId) {
        if (signalId == null) return;
        if (!inFlight.add(signalId)) return;

        try {
            signalNotificationRetryTemplate.execute(context -> {
                sendOnce(signalId);
                return null;
            });
        } catch (Exception ex) {
            log.warn("Notification send failed for signalId={}, will retry later: {}", signalId, ex.getMessage());
        } finally {
            inFlight.remove(signalId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOnce(Long signalId) {
        log.info("sendOnce called, signalId={}", signalId);

        Signal fresh = signalRepository.findById(signalId).orElse(null);
        if (fresh == null) {
            log.warn("sendOnce skipped: signal not found, signalId={}", signalId);
            return;
        }
        if (Boolean.TRUE.equals(fresh.getTelegramSent())) {
            log.info("sendOnce skipped: already sent, signalId={}", signalId);
            return;
        }

        SignalNotificationPayload payload = new SignalNotificationPayload(
                fresh.getId(),
                fresh.getMarketId(),
                fresh.getMarketQuestion(),
                fresh.getMarketUrl(),
                fresh.getOutcome(),
                fresh.getWhalesCount(),
                fresh.getTotalAmount(),
                fresh.getFirstBetAt(),
                fresh.getLastBetAt(),
                List.of(),
                fresh.getReason()
        );

        notificationService.sendSignalNotification(payload);

        int updated;
        try {
            updated = signalRepository.markTelegramSent(signalId, Instant.now());
        } catch (DataAccessException ex) {
            throw ex;
        }

        if (updated == 0) {
            log.debug("Signal {} already marked telegramSent by another worker", signalId);
        }

        fresh.setTelegramSent(true);
        fresh.setTelegramSentAt(Instant.now());
        signalRepository.saveAndFlush(fresh);

        log.info("sendOnce success, signalId={}", signalId);
    }
}
