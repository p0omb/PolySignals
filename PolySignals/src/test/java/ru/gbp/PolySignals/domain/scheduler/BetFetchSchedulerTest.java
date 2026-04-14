package ru.gbp.PolySignals.domain.scheduler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
import ru.gbp.PolySignals.domain.entity.Bet;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;
import ru.gbp.PolySignals.domain.service.BetService;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class BetFetchSchedulerTest {

    @MockitoBean
    private WhaleRepository whaleRepository;

    @MockitoBean
    private DataApiClient dataApiClient;

    @MockitoBean
    private BetService betService;

    @Autowired
    private BetFetchScheduler betFetchScheduler;

//    @Test
//    void fetchesTradesForEachWhale() {
//        Whale w1 = whale("u1");
//        Whale w2 = whale("u2");
//
//        when(whaleRepository.findTopWhales(100)).thenReturn(List.of(w1, w2));
//        when(dataApiClient.getUserTrades(anyString())).thenReturn(List.of(new UserTradeDto()));
//
//        betFetchScheduler.fetchBets();
//
//        verify(dataApiClient, times(2)).getUserTrades(anyString());
//        verify(betService, times(2)).saveNewBets(anyString(), org.mockito.ArgumentMatchers.anyList());
//    }

    @Test
    void fetchesTradesForEachWhale() {
        Whale w1 = whale("0x56687bf447db6ffa42ffe2204a05edaa20f55839");
        Whale w2 = whale("0x9e2b3a7d4c8f1e6a9b2c3d4e5f6a7b8c9d0e1f2a");

        when(whaleRepository.findTopWhales(100)).thenReturn(List.of(w1, w2));
        when(dataApiClient.getUserTradesSince(anyString(), any(Instant.class), eq(200)))
                .thenReturn(List.of(new UserTradeDto()));
        when(betService.saveNewBets(anyString(), anyList())).thenReturn(List.of(new Bet()));

        betFetchScheduler.run();

        verify(dataApiClient, times(2)).getUserTradesSince(anyString(), any(Instant.class), eq(200));
        verify(betService, times(2)).saveNewBets(anyString(), anyList());
    }


    private Whale whale(String userId) {
        Whale whale = new Whale();
        whale.setUserId(userId);
        whale.setUserName("whale-" + userId);
        whale.setVolume(new BigDecimal("100"));
        whale.setLastSeenAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        return whale;
    }
}
