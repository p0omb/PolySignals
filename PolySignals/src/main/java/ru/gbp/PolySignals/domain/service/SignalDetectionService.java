package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.gbp.PolySignals.SignalCandidateProjection;
import ru.gbp.PolySignals.client.polymarket.GammaApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
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
    private final GammaApiClient gammaApiClient;

    @Transactional
    public void detectAndSendSignals(Duration period, Duration cooldown) {
        Instant from = Instant.now().minus(period);
        List<SignalCandidateProjection> candidates =
                betRepository.findSignalCandidates(from); // JPQL или native

        for (SignalCandidateProjection c : candidates) {
            boolean existsRecent = signalRepository.existsRecentSignal(
                    c.getMarketId(), c.getOutcome(), Instant.now().minus(cooldown));
            if (existsRecent) {
                continue;
            }

            Signal signal = new Signal();
            signal.setMarketId(c.getMarketId());
            signal.setOutcome(c.getOutcome());
            signal.setWhalesCount(c.getWhalesCount());
            signal.setTotalAmount(c.getTotalAmount());
            signal.setPeriodHours((int) period.toHours());
            signal.setCreatedAt(Instant.now());
            signalRepository.save(signal);

            MarketDetailsDto market = gammaApiClient.getMarket(c.getMarketId());
        }
    }
}