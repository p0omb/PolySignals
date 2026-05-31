package ru.gbp.PolySignals.client.polymarket;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.test.util.ReflectionTestUtils;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.*;

class GammaApiClientTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private GammaApiClient gammaApiClient;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder()
                .baseUrl(wiremock.getRuntimeInfo().getHttpBaseUrl())
                .build();

        gammaApiClient = new GammaApiClient(webClient, retryTemplate());
        ReflectionTestUtils.setField(gammaApiClient, "marketCache", Caffeine.newBuilder().build());
    }


    @Test
    void getMarket_returnsMarketDetails_whenApiRespondsOk() {
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m1"))
                .willReturn(okJson("[{\"conditionId\":\"m1\",\"question\":\"Q\",\"slug\":\"q\",\"clobTokenIds\":\"[\\\"t1\\\",\\\"t2\\\"]\",\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\"}]")));

        MarketDetailsDto result = gammaApiClient.getMarket("m1");
        assertNotNull(result);
        assertEquals("Q", result.getQuestion());
        assertEquals("q", result.getSlug());
        assertFalse(result.getClobTokenIds().isEmpty());
        assertEquals("t1", result.getClobTokenIds().get(0));
        assertEquals("t2", result.getClobTokenIds().get(1));

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m1")));
    }

    @Test
    void getMarket_retriesOnServerError_thenSucceeds() {
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m2"))
                .inScenario("gamma-retry-500")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("SECOND"));
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m2"))
                .inScenario("gamma-retry-500")
                .whenScenarioStateIs("SECOND")
                .willReturn(serverError())
                .willSetStateTo("SUCCESS"));
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m2"))
                .inScenario("gamma-retry-500")
                .whenScenarioStateIs("SUCCESS")
                .willReturn(okJson("[{\"conditionId\":\"m2\",\"question\":\"Q2\",\"slug\":\"q2\",\"clobTokenIds\":\"[\\\"token-yes\\\",\\\"token-no\\\"]\",\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\"}]")));

        MarketDetailsDto result = gammaApiClient.getMarket("m2");
        assertNotNull(result);
        assertEquals("Q2", result.getQuestion());
        assertEquals("q2", result.getSlug());
        assertEquals(2, result.getClobTokenIds().size());

        wiremock.verify(3, getRequestedFor(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m2")));
    }

    @Test
    void getMarket_retriesOn429_thenSucceeds() {
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m3"))
                .inScenario("gamma-retry-429")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("SUCCESS"));
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m3"))
                .inScenario("gamma-retry-429")
                .whenScenarioStateIs("SUCCESS")
                .willReturn(okJson("[{\"conditionId\":\"m3\",\"question\":\"Q3\",\"slug\":\"q3\",\"clobTokenIds\":\"[\\\"token-yes\\\",\\\"token-no\\\"]\",\"outcomes\":\"[\\\"Yes\\\",\\\"No\\\"]\"}]")));

        MarketDetailsDto result = gammaApiClient.getMarket("m3");
        assertNotNull(result);
        assertEquals("Q3", result.getQuestion());
        assertEquals("q3", result.getSlug());
        assertEquals(2, result.getClobTokenIds().size());

        wiremock.verify(2, getRequestedFor(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m3")));
    }

    @Test
    void getMarket_doesNotRetryOn404() {
        wiremock.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m404"))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        MarketDetailsDto result = gammaApiClient.getMarket("m404");
        assertNull(result);

        wiremock.verify(1, getRequestedFor(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("m404")));
    }

    @Test
    void getMarket_throwsIllegalArgumentException_whenMarketIdBlank() {
        assertThrows(IllegalArgumentException.class, () -> gammaApiClient.getMarket(" "));
    }

    private RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(WebClientRequestException.class, true);
        retryable.put(WebClientResponseException.class, true);
        template.setRetryPolicy(new SimpleRetryPolicy(3, retryable, true) {
            @Override
            public boolean canRetry(RetryContext context) {
                Throwable t = context.getLastThrowable();
                if (t instanceof WebClientResponseException ex) {
                    int code = ex.getStatusCode().value();
                    if (code != 429 && !ex.getStatusCode().is5xxServerError()) {
                        return false;
                    }
                }
                return super.canRetry(context);
            }
        });
        template.setBackOffPolicy(new NoBackOffPolicy());
        return template;
    }
}