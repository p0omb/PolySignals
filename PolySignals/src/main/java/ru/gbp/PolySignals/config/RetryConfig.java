package ru.gbp.PolySignals.config;

import lombok.RequiredArgsConstructor;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ru.gbp.PolySignals.config.properties.HikariProperties;
import ru.gbp.PolySignals.config.properties.PolymarketRetryProperties;
import ru.gbp.PolySignals.config.properties.SignalProperties;

@Configuration
@EnableRetry
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

    @Bean
    public RetryOperationsInterceptor polymarketRetryInterceptor(RetryTemplate polymarketRetryTemplate) {
        return RetryInterceptorBuilder.stateless()
                .retryOperations(polymarketRetryTemplate)
                .build();
    }

    @Bean
    public Advisor polymarketClientRetryAdvisor(RetryOperationsInterceptor polymarketRetryInterceptor) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                "execution(* ru.gbp.PolySignals.client.polymarket.DataApiClient.*(..)) || " +
                "execution(* ru.gbp.PolySignals.client.polymarket.GammaApiClient.*(..))"
        );
        Advice advice = polymarketRetryInterceptor;
        return new DefaultPointcutAdvisor(pointcut, advice);
    }
}

