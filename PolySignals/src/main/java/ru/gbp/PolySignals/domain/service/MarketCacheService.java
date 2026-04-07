package ru.gbp.PolySignals.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.gbp.PolySignals.client.polymarket.GammaApiClient;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketCacheService {

    private final GammaApiClient gammaApiClient;

    @CacheEvict(value = "markets", key = "#marketId")
    public void refreshMarket(String marketId) {
        gammaApiClient.getMarket(marketId);
    }

    @Cacheable(value = "markets", key = "#marketId")
    public MarketDetailsDto getMarket(String marketId) {
        return gammaApiClient.getMarket(marketId);
    }
}

