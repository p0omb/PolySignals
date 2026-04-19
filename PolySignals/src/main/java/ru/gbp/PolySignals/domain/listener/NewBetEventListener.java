package ru.gbp.PolySignals.domain.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.event.NewBetEvent;
import ru.gbp.PolySignals.domain.service.SignalDetectionService;

import java.time.Duration;

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
        signalDetectionService.checkSignalForNewBet(newBet, SIGNAL_WINDOW, SIGNAL_COOLDOWN);
    }
}
