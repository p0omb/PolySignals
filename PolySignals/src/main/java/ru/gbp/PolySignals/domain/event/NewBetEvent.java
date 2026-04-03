package ru.gbp.PolySignals.domain.event;

import ru.gbp.PolySignals.domain.entity.Bet;

import java.util.Objects;

public record NewBetEvent(Bet bet) {

    public NewBetEvent {
        Objects.requireNonNull(bet, "bet must not be null");
    }
}

