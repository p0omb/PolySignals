package ru.gbp.PolySignals.client.polymarket;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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

import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class DataApiClientTest extends AbstractPostgresIntegrationTest {

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

    @Autowired DataApiClient dataApiClient;

    @BeforeEach
    void reset() {
        wiremock.resetAll();
    }

    @Test
    void retriesOn500AndEventuallySucceeds() {
        wiremock.stubFor(get(urlPathEqualTo("/trades/u1"))
                .inScenario("server-error")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("second"));

        wiremock.stubFor(get(urlPathEqualTo("/trades/u1"))
                .inScenario("server-error")
                .whenScenarioStateIs("second")
                .willReturn(okJson("[]")));

        List<?> trades = dataApiClient.getUserTrades("u1",afterNow(),200);

        assertThat(trades).isNotNull();
        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/trades/u1")));
    }

    @Test
    void retriesOn429AndEventuallySucceeds() {
        wiremock.stubFor(get(urlPathEqualTo("/trades/u2"))
                .withQueryParam("after", matching(".*"))
                .withQueryParam("limit", matching(".*"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("second"));

        // Заглушка для повторного запроса
        wiremock.stubFor(get(urlPathEqualTo("/trades/u2"))
                .withQueryParam("after", matching(".*"))
                .withQueryParam("limit", matching(".*"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("second")
                .willReturn(okJson("[]")));

        List<?> trades = dataApiClient.getUserTrades("u2", afterNow(), 200);

        assertThat(trades).isNotNull();
        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/trades/u2")));
    }

    private Instant afterNow() {
        return Instant.now().minus(Duration.ofDays(1));
    }
}
