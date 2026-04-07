package ru.gbp.PolySignals.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.BetRepository;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

@ExtendWith(MockitoExtension.class)
class BetServiceTest {

    @Mock
    BetRepository betRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    WhaleRepository whaleRepository;
    @InjectMocks
    BetService betService;

    @Test
    void savesNewBetsAndPublishesEvent() {
        Whale whale = whale("user1");
        when(whaleRepository.findByUserId("user1")).thenReturn(Optional.of(whale));
        when(betRepository.existsByUniqueKey("t1")).thenReturn(false);
        when(betRepository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserTradeDto trade = trade("m1", "yes", "20", Instant.parse("2026-01-01T10:00:00Z"));
        List<UserTradeDto> trades = List.of(trade);

        List<Bet> result = betService.saveNewBets("user1", List.of(trade));

        verify(betRepository).save(any(Bet.class));
        verify(eventPublisher).publishEvent(any());
        assertEquals(1, result.size());
    }

    @Test
    void deduplicatesExistingBets() {
        when(betRepository.existsByUniqueKey("t1")).thenReturn(true);

        UserTradeDto trade = trade("m1", "yes", "20", Instant.now());
        // Убедимся, что uniqueKey формируется так же, как в сервисе
        // Предположим, что uniqueKey = externalBetId или комбинация полей
        // Для теста можно мокировать existsByUniqueKey с любым аргументом
        List<UserTradeDto> trades = List.of(trade);

        List<Bet> result = betService.saveNewBets("user1", trades);


        verify(betRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
        org.junit.jupiter.api.Assertions.assertTrue(result.isEmpty());
    }

    private Whale whale(String userId) {
        Whale whale = new Whale();
        whale.setUserId(userId);
        whale.setUserName("user-" + userId);
        whale.setVolume(BigDecimal.TEN);
        whale.setLastSeenAt(Instant.now());
        return whale;
    }

    private UserTradeDto trade(String marketId, String outcome, String size, Instant timestamp) {
        UserTradeDto dto = new UserTradeDto();
        dto.setMarketId(marketId);
        dto.setOutcome(outcome);
        dto.setSize(new BigDecimal(size));   // поле называется size, а не amount
        dto.setTimestamp(timestamp);
        // Если нужен externalBetId, добавьте его (например, через setTransactionHash)
        dto.setTransactionHash("tx_" + System.currentTimeMillis());
        return dto;
    }
}
