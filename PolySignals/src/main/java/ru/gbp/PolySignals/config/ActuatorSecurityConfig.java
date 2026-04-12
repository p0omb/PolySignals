package ru.gbp.PolySignals.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

@Configuration
@EnableWebSecurity
public class ActuatorSecurityConfig {

    private static final List<IpAddressMatcher> HEALTH_ALLOWED_IPS = List.of(
            new IpAddressMatcher("127.0.0.1/32"),
            new IpAddressMatcher("::1/128"),
            new IpAddressMatcher("10.0.0.0/8"),
            new IpAddressMatcher("172.16.0.0/12"),
            new IpAddressMatcher("192.168.0.0/16")
    );

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .access((authentication, context) -> healthIpAccess(context))
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    private AuthorizationDecision healthIpAccess(RequestAuthorizationContext context) {
        boolean allowed = HEALTH_ALLOWED_IPS.stream().anyMatch(m -> m.matches(context.getRequest()));
        return new AuthorizationDecision(allowed);
    }
}