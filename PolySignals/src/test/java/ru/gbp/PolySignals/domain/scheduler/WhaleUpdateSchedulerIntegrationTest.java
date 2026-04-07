package ru.gbp.PolySignals.domain.scheduler;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import ru.gbp.PolySignals.domain.entity.Whale;
import ru.gbp.PolySignals.domain.repository.WhaleRepository;
import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class WhaleUpdateSchedulerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final WireMockServer wiremock = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        wiremock.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("polymarket.data-api.base-url", wiremock::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        wiremock.stop();
    }

    @Autowired WhaleUpdateScheduler whaleUpdateScheduler;
    @Autowired WhaleRepository whaleRepository;

    @BeforeEach
    void reset() {
        wiremock.resetAll();
    }

    @Test
    void updatesWhalesFromLeaderboard() {
        wiremock.stubFor(get(urlPathEqualTo("/leaderboard/users"))
                .withQueryParam("limit", equalTo("100"))
                .willReturn(okJson("""
                        [{"userId":"u1","label":"whale-1","volume":100}]
                        """)));

        whaleUpdateScheduler.run();

        assertThat(whaleRepository.findByUserId("u1"))
                .isPresent()
                .get()
                .satisfies(whale -> {
                    assertThat(whale.getUserName()).isEqualTo("whale-1");
                    assertThat(whale.getVolume()).isEqualByComparingTo(new BigDecimal("100"));
                });

        wiremock.verify(getRequestedFor(urlPathEqualTo("/leaderboard/users"))
                .withQueryParam("limit", equalTo("100")));
    }
}
