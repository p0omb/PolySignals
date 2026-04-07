package ru.gbp.PolySignals.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ru.gbp.PolySignals.client.polymarket.DataApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.LeaderboardUserDto;
import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;

@ExtendWith(MockitoExtension.class)
class WhaleServiceTest {

    @Mock DataApiClient dataApiClient;
    @Mock WhaleRepository whaleRepository;
    @InjectMocks WhaleService whaleService;

    private LeaderboardUserDto leaderboardUser(String userId, String userName, String volume) {
        LeaderboardUserDto dto = new LeaderboardUserDto();
        dto.setUserId(userId);
        dto.setUserName(userName);
        dto.setVolume(new BigDecimal(volume));
        dto.setProfit(BigDecimal.ZERO);
        return dto;
    }

    @Test
    void updatesExistingAndCreatesNewWhales() {
        when(whaleRepository.findByUserId("u1")).thenReturn(Optional.of(existingWhale()));
        when(whaleRepository.findByUserId("u2")).thenReturn(Optional.empty());
        when(whaleRepository.save(any(Whale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<LeaderboardUserDto> leaderboard = List.of(
                leaderboardUser("u1", "whale-1", "100"),
                leaderboardUser("u2", "whale-2", "200")
        );

        ArgumentCaptor<Whale> captor = ArgumentCaptor.forClass(Whale.class);

        whaleService.updateWhales(leaderboard);

        verify(whaleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Whale::getUserId)
                .containsExactly("u1", "u2");
        assertThat(captor.getAllValues())
                .extracting(Whale::getUserName)
                .containsExactly("whale-1", "whale-2");
    }

    private Whale existingWhale() {
        Whale whale = new Whale();
        whale.setUserId("u1");
        whale.setUserName("old");
        whale.setVolume(new BigDecimal("50"));
        whale.setLastSeenAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        return whale;
    }
}
