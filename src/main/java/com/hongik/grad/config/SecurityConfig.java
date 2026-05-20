package com.hongik.grad.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder security configuration for PR-01.
 *
 * <p>Spring Security is on the classpath via {@code spring-boot-starter-security} but PR-01 has no
 * authentication filter yet. We permit all requests so {@code /actuator/health} and the future
 * controllers respond instead of returning the default 401.
 *
 * <p>TODO PR-03: replace with SupabaseJwtAuthenticationFilter chain (stateless, JWT required on
 * everything except {@code /actuator/health} and OpenAPI).
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
