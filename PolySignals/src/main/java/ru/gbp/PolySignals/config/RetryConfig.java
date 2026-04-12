package ru.gbp.PolySignals.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import ru.gbp.PolySignals.config.properties.HikariProperties;
import ru.gbp.PolySignals.config.properties.PolymarketRetryProperties;
import ru.gbp.PolySignals.config.properties.SignalProperties;

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

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(retryProperties.getMaxAttempts());
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.getInitialDelay().toMillis());
        backOffPolicy.setMultiplier(retryProperties.getMultiplier());

        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);
        return template;
    }
}
