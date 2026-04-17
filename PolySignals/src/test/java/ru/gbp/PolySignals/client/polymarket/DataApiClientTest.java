//package ru.gbp.PolySignals.client.polymarket;
//
//import static com.github.tomakehurst.wiremock.client.WireMock.*;
//import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.List;
//
//import org.junit.jupiter.api.AfterAll;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//
//import com.github.tomakehurst.wiremock.WireMockServer;
//import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
//
//import ru.gbp.PolySignals.support.AbstractPostgresIntegrationTest;
//
//@SpringBootTest
//@ActiveProfiles("test")
//class DataApiClientTest extends AbstractPostgresIntegrationTest {
//
//    private static final WireMockServer wiremock = new WireMockServer(wireMockConfig().dynamicPort());
//
//    static {
//        wiremock.start();
//    }
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry registry) {
//        registry.add("polymarket.data-api.base-url", wiremock::baseUrl);
//    }
//
//    @AfterAll
//    static void tearDown() {
//        wiremock.stop();
//    }
//
//    @Autowired DataApiClient dataApiClient;
//
//    @BeforeEach
//    void reset() {
//        wiremock.resetAll();
//    }
//
//    @Test
//    void retriesOn500AndEventuallySucceeds() {
//        wiremock.stubFor(get(urlPathEqualTo("/users/u1/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+"))
//                .inScenario("server-error")
//                .whenScenarioStateIs(STARTED)
//                .willReturn(serverError())
//                .willSetStateTo("second"));
//
//        wiremock.stubFor(get(urlPathEqualTo("/users/u1/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+"))
//                .inScenario("server-error")
//                .whenScenarioStateIs("second")
//                .willReturn(okJson("[]")));
//
//        List<?> trades = dataApiClient.getUserTrades("u1", afterNow(), 200);
//
//        assertThat(trades).isNotNull();
//        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/users/u1/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+")));
//    }
//
//    @Test
//    void retriesOn429AndEventuallySucceeds() {
//        wiremock.stubFor(get(urlPathEqualTo("/users/u2/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+"))
//                .inScenario("rate-limit")
//                .whenScenarioStateIs(STARTED)
//                .willReturn(aResponse().withStatus(429))
//                .willSetStateTo("second"));
//
//        wiremock.stubFor(get(urlPathEqualTo("/users/u2/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+"))
//                .inScenario("rate-limit")
//                .whenScenarioStateIs("second")
//                .willReturn(okJson("[]")));
//
//        List<?> trades = dataApiClient.getUserTrades("u2", afterNow(), 200);
//
//        assertThat(trades).isNotNull();
//        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/users/u2/trades"))
//                .withQueryParam("limit", equalTo("200"))
//                .withQueryParam("after_timestamp", matching("\\d+")));
//    }
//
//    private Instant afterNow() {
//        return Instant.now().minus(Duration.ofDays(1));
//    }
//}
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

import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;
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
        wiremock.stubFor(get(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x56687bf447db6ffa42ffe2204a05edaa20f55839"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true"))
                .inScenario("server-error")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("second"));

        wiremock.stubFor(get(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x56687bf447db6ffa42ffe2204a05edaa20f55839"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true"))
                .inScenario("server-error")
                .whenScenarioStateIs("second")
                .willReturn(okJson("[]")));

        List<UserTradeDto> trades = dataApiClient.getUserTrades("0x56687bf447db6ffa42ffe2204a05edaa20f55839", 200, Instant.now().minus(Duration.ofDays(1)));

        assertThat(trades).isNotNull();
        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x56687bf447db6ffa42ffe2204a05edaa20f55839"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true")));
    }

    @Test
    void retriesOn429AndEventuallySucceeds() {
        wiremock.stubFor(get(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x9e2b3a7d4c8f1e6a9b2c3d4e5f6a7b8c9d0e1f2a"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true"))
                .inScenario("rate-limit")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("second"));

        wiremock.stubFor(get(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x9e2b3a7d4c8f1e6a9b2c3d4e5f6a7b8c9d0e1f2a"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true"))
                .inScenario("rate-limit")
                .whenScenarioStateIs("second")
                .willReturn(okJson("[]")));

        List<UserTradeDto> trades = dataApiClient.getUserTrades("0x9e2b3a7d4c8f1e6a9b2c3d4e5f6a7b8c9d0e1f2a", 200, Instant.now().minus(Duration.ofDays(1)));

        assertThat(trades).isNotNull();
        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x9e2b3a7d4c8f1e6a9b2c3d4e5f6a7b8c9d0e1f2a"))
                .withQueryParam("limit", equalTo("200"))
                .withQueryParam("after_timestamp", matching("\\d+"))
                .withQueryParam("takerOnly", equalTo("true")));
    }

    @Test
    void getUserTradesSinceFiltersOutOldTrades() {
        Instant now = Instant.now();
        Instant yesterday = now.minus(Duration.ofDays(1));

        wiremock.stubFor(get(urlPathEqualTo("/trades"))
                .withQueryParam("user", equalTo("0x56687bf447db6ffa42ffe2204a05edaa20f55839"))
                .willReturn(okJson("""
                        [
                          {
                            "conditionId": "0xdd22472e552920b8438158ea7238bfadfa4f736aa4cee91a6b86c39ead110917",
                            "size": 100.0,
                            "price": 0.75,
                            "timestamp": %d,
                            "asset": "YES",
                            "side": "BUY"
                          },
                          {
                            "conditionId": "0xaa11361e441809a7327127d9b129efceb3e6f625bb3ddf80a6a5c39ead220828",
                            "size": 50.0,
                            "price": 0.25,
                            "timestamp": %d,
                            "asset": "NO",
                            "side": "SELL"
                          }
                        ]
                        """.formatted(yesterday.getEpochSecond(), now.getEpochSecond()))));

        List<UserTradeDto> filtered = dataApiClient.getUserTradesSince(
                "0x56687bf447db6ffa42ffe2204a05edaa20f55839",
                yesterday.plus(Duration.ofMinutes(1)),
                5000
        );

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getOutcome()).isEqualTo("NO");
    }
}