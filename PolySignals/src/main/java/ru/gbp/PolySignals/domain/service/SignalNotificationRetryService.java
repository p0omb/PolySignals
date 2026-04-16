package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSignalCreated(SignalCreatedEvent event) {
        if (event == null || event.signalId() == null) return;
        trySend(event.signalId());
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.notification.retry.delay:60000}")
    public void retryUnsentSignals() {
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

    private void sendOnce(Long signalId) {
        Signal signal = signalRepository.findById(signalId).orElse(null);
        if (signal == null) return;
        if (Boolean.TRUE.equals(signal.getTelegramSent())) return;

        SignalNotificationPayload payload = new SignalNotificationPayload(
                signal.getId(),
                signal.getMarketId(),
                signal.getMarketQuestion(),
                signal.getMarketUrl(),
                signal.getOutcome(),
                signal.getWhalesCount(),
                signal.getTotalAmount(),
                signal.getFirstBetAt(),
                signal.getLastBetAt(),
                List.of(),
                signal.getReason()
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
    }
}

