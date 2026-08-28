package com.megharaj.orderinventory.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configures HTTP Basic authentication and role-based authorization for the API and actuator.
 *
 * <p>Write operations require elevated roles, reads require any authenticated account, liveness and
 * readiness probes stay public, and all other actuator endpoints are restricted. The API is
 * stateless: no HTTP session is created and CSRF protection is disabled because there is no
 * cookie-based session to protect.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    static final String CATALOG_ADMIN = "CATALOG_ADMIN";
    static final String ORDER_CLIENT = "ORDER_CLIENT";
    static final String MONITORING = "MONITORING";
    static final String ADMIN = "ADMIN";

    /**
     * Builds the security filter chain describing authentication and per-endpoint authorization.
     *
     * @param http the HTTP security builder
     * @return the configured filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole(MONITORING, ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**")
                        .hasAnyRole(CATALOG_ADMIN, ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**")
                        .hasAnyRole(CATALOG_ADMIN, ORDER_CLIENT, ADMIN)
                        .requestMatchers("/api/v1/orders/**").hasAnyRole(ORDER_CLIENT, ADMIN)
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Encodes and verifies passwords using BCrypt.
     *
     * @return the BCrypt password encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Materializes the configured accounts into an in-memory user store.
     *
     * @param properties validated account configuration (guaranteed non-empty)
     * @return the in-memory user details manager
     */
    @Bean
    InMemoryUserDetailsManager userDetailsManager(AppSecurityProperties properties) {
        List<UserDetails> users = new ArrayList<>(properties.users().size());
        for (AppSecurityProperties.UserAccount account : properties.users()) {
            users.add(User.withUsername(account.username())
                    .password(account.password())
                    .roles(account.roles().toArray(String[]::new))
                    .build());
        }
        return new InMemoryUserDetailsManager(users);
    }
}
