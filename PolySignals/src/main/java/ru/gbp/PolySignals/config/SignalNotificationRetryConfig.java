package ru.gbp.PolySignals.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import ru.gbp.PolySignals.config.properties.SignalNotificationRetryProperties;

@Configuration
public class SignalNotificationRetryConfig {

    @Bean
    public RetryTemplate signalNotificationRetryTemplate(SignalNotificationRetryProperties props) {
        RetryTemplate template = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(props.getMaxAttempts());
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(props.getInitialInterval().toMillis());
        backOff.setMultiplier(props.getMultiplier());
        backOff.setMaxInterval(props.getMaxInterval().toMillis());
        template.setBackOffPolicy(backOff);

        return template;
    }
}

