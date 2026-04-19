package ru.gbp.PolySignals.domain.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.event.NewBetEvent;
import ru.gbp.PolySignals.domain.service.SignalDetectionService;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewBetEventListener {

    private static final Duration SIGNAL_WINDOW = Duration.ofDays(4);
    private static final Duration SIGNAL_COOLDOWN = Duration.ofHours(6);

    private final SignalDetectionService signalDetectionService;

//    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewBet(NewBetEvent event) {
        if (event == null || event.bet() == null) {
            return;
        }

        Bet newBet = event.bet();
        log.info("NewBetEventListener received event for bet id={}", newBet.getId());

        try {
            signalDetectionService.checkSignalForNewBet(newBet, SIGNAL_WINDOW, SIGNAL_COOLDOWN);
            log.info("checkSignalForNewBet called for bet id={}", newBet.getId());
        } catch (Exception ex) {
            log.error("NewBetEventListener failed for bet id={}", newBet.getId(), ex);
            throw ex;
        }
    }
}
