package ru.gbp.PolySignals.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import ru.gbp.PolySignals.config.properties.SignalNotificationRetryProperties;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Configuration
public class SignalNotificationRetryConfig {

    @Bean
    public RetryTemplate signalNotificationRetryTemplate(SignalNotificationRetryProperties props) {
        RetryTemplate template = new RetryTemplate();

        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(WebClientRequestException.class, true);
        retryable.put(SocketTimeoutException.class, true);
        retryable.put(TimeoutException.class, true);
        retryable.put(IOException.class, true);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(props.getMaxAttempts(), retryable, true);
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(props.getInitialInterval().toMillis());
        backOff.setMultiplier(props.getMultiplier());
        backOff.setMaxInterval(props.getMaxInterval().toMillis());
        template.setBackOffPolicy(backOff);

        return template;
    }
}
