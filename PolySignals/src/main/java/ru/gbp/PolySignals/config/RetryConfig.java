package ru.gbp.PolySignals.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.gbp.PolySignals.config.properties.HikariProperties;
import ru.gbp.PolySignals.config.properties.PolymarketRetryProperties;
import ru.gbp.PolySignals.config.properties.SignalProperties;

import java.io.IOException;
import java.net.SocketTimeoutException;
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
        retryableExceptions.put(WebClientRequestException.class, true);
        retryableExceptions.put(WebClientResponseException.class, true);
        retryableExceptions.put(TimeoutException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        retryableExceptions.put(IOException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                retryProperties.getMaxAttempts(),
                retryableExceptions,
                true
        ) {
            @Override
            public boolean canRetry(RetryContext context) {
                Throwable t = context.getLastThrowable();
                if (t instanceof WebClientResponseException ex) {
                    int code = ex.getStatusCode().value();
                    if (code != 429 && !ex.getStatusCode().is5xxServerError()) {
                        return false; // 4xx (кроме 429) не ретраим
                    }
                }
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
}
