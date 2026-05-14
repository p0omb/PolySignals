package ru.gbp.PolySignals.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.gbp.PolySignals.client.polymarket.PolymarketApiErrorHandler;
import ru.gbp.PolySignals.client.polymarket.dto.MarketDetailsDto;
import ru.gbp.PolySignals.config.properties.HikariProperties;
import ru.gbp.PolySignals.config.properties.PolymarketRetryProperties;
import ru.gbp.PolySignals.config.properties.SignalProperties;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({
        HikariProperties.class,
        SignalProperties.class,
        PolymarketRetryProperties.class
})
public class RetryConfig {

    private final PolymarketRetryProperties retryProperties;

    @Bean
    public RetryTemplate polymarketRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        // включаем WebClientRequestException и WebClientResponseException (обрабатываем их статусы в canRetry)
        retryableExceptions.put(WebClientRequestException.class, true);
        retryableExceptions.put(WebClientResponseException.class, true);
        // сетевые/таймаутные исключения
        retryableExceptions.put(TimeoutException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        retryableExceptions.put(IOException.class, true);
        // если PolymarketApiErrorHandler.buildException возвращает этот тип — учитываем его
        retryableExceptions.put(PolymarketApiErrorHandler.PolymarketApiException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                retryProperties.getMaxAttempts(),
                retryableExceptions,
                true
        ) {
            @Override
            public boolean canRetry(RetryContext context) {
                Throwable t = context.getLastThrowable();
                if (t == null) {
                    return super.canRetry(context);
                }

                // WebClientResponseException: ретрай только для 429 и 5xx
                if (t instanceof WebClientResponseException wcre) {
                    int code = wcre.getStatusCode().value();
                    if (code == 429 || (code >= 500 && code < 600)) {
                        return super.canRetry(context);
                    }
                    return false; // 4xx (включая 404) — не ретраить
                }

                // Если обёртка PolymarketApiException — смотрим флаг retryable
                if (t instanceof PolymarketApiErrorHandler.PolymarketApiException pae) {
                    return pae.isRetryable() && super.canRetry(context);
                }

                // Для остальных типов — fallback на стандартную логику (учитывает карту exception types)
                return super.canRetry(context);
            }
        };

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.getInitialDelay().toMillis());
        backOffPolicy.setMultiplier(retryProperties.getMultiplier());

        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);
        return template;
    }

    @Bean
    public Cache<String, MarketDetailsDto> gammaMarketCache(
            @Value("${app.polymarket.gamma.market-cache-ttl:PT30M}") Duration ttl,
            @Value("${app.polymarket.gamma.market-cache-size:1000}") long maxSize
    ) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .build();
    }
}
