package ru.gbp.PolySignals.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.gbp.PolySignals.config.properties.PolymarketApiProperties;

import java.time.Duration;

@Configuration
@EnableRetry
@EnableConfigurationProperties(PolymarketApiProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient dataApiWebClient(WebClient.Builder builder, PolymarketApiProperties polymarketApiProperties) {
        return builder
                .baseUrl(polymarketApiProperties.getDataApi().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(5))
                                .compress(true)
                ))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    @Bean
    public WebClient gammaApiWebClient(WebClient.Builder builder, PolymarketApiProperties polymarketApiProperties) {
        return builder
                .baseUrl(polymarketApiProperties.getGammaApi().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(5))
                ))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // логирование URL, метода, заголовков
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            // логирование статуса и заголовков
            return Mono.just(clientResponse);
        });
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}