package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.gbp.PolySignals.domain.repository.SignalCandidateProjection;
import ru.gbp.PolySignals.client.telegram.SignalNotificationPayload;
import ru.gbp.PolySignals.client.telegram.NotificationService;
import ru.gbp.PolySignals.domain.entity.Signal;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.SignalRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignalDetectionService {

    private final BetRepository betRepository;
    private final SignalRepository signalRepository;
    private final NotificationService notificationService;

    public void detectAndSendSignals(Duration period, Duration cooldown) {
        Instant now = Instant.now();
        Instant from = now.minus(period);
        Instant to = now;
        int minWhales = 2;

        List<SignalCandidateProjection> candidates =
                betRepository.findSignalCandidates(from, to, minWhales);

        for (SignalCandidateProjection c : candidates) {
            boolean existsRecent = signalRepository.existsRecentSignal(
                    c.getMarketId(), c.getOutcome(), now.minus(cooldown));
            if (existsRecent) {
                continue;
            }

            Signal signal = new Signal();
            signal.setMarketId(c.getMarketId());
            signal.setOutcome(c.getOutcome());
            signal.setWhalesCount(c.getWhalesCount());
            signal.setTotalAmount(c.getTotalAmount());
            signal.setPeriodHours((int) period.toHours());
            signal.setFirstBetAt(from);
            signal.setLastBetAt(to);
            signal.setReason("Matched " + c.getWhalesCount() + " whales on same market/outcome");
            signal.setCreatedAt(now);

            signalRepository.save(signal);

            SignalNotificationPayload payload = buildNotificationPayload(signal);
            notificationService.sendSignalNotification(payload);
        }
    }

    private SignalNotificationPayload buildNotificationPayload(Signal signal) {
        return new SignalNotificationPayload(
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
    }
}