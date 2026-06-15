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
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

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
                    transactionTemplate.executeWithoutResult(tx -> sendOnce(signalId));
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
        log.info("sendOnce called for signalId={}", signalId);

        Signal signal = signalRepository.findById(signalId).orElse(null);
        if (signal == null) {
            log.warn("Signal not found, signalId={}", signalId);
            return;
        }
        if (Boolean.TRUE.equals(signal.getTelegramSent())) {
            log.info("Skipping already sent signalId={}", signalId);
            return;
        }
        String marketUrl = (signal.getSlug() != null && !signal.getSlug().isBlank())
                ? "https://polymarket.com/event/" + signal.getSlug()
                : signal.getMarketUrl();


        SignalNotificationPayload payload = new SignalNotificationPayload(
                signal.getId(),
                signal.getMarketId(),
                signal.getMarketQuestion(),
                marketUrl,
                signal.getOutcome(),
                signal.getWhalesCount(),
                signal.getTotalAmount(),
                signal.getFirstBetAt(),
                signal.getLastBetAt(),
                // важно: реальные whaleLabels из сигнала
                signal.getWhaleLabels(),
                signal.getReason(),
                signal.getWhaleWeight()
        );

        try {
            log.info("Sending Telegram notification for signalId={}", signalId);
            notificationService.sendSignalNotification(payload);

            signal.setTelegramSent(true);
            signal.setTelegramSentAt(Instant.now());
            signalRepository.save(signal);

            // либо, если используется атомарный update:
            // signalRepository.markTelegramSent(signalId, Instant.now());

            log.info("Telegram notification sent successfully, signalId={}", signalId);
        } catch (Exception ex) {
            log.error("Failed to send Telegram notification, signalId={}: {}", signalId, ex.getMessage(), ex);
            // не менять telegramSent / telegramSentAt
            throw ex;
        }
    }
}
