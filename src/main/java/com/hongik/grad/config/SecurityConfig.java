package com.hongik.grad.config;

import com.hongik.grad.auth.filter.SupabaseJwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless Supabase-JWT-backed security chain.
 *
 * <p>Every request except health, OpenAPI metadata, and CORS preflight must carry a valid Bearer
 * token verified by {@link SupabaseJwtAuthenticationFilter}.
 */
@Configuration
public class SecurityConfig {

  private final SupabaseJwtAuthenticationFilter supabaseJwtFilter;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;
  private final JsonAccessDeniedHandler accessDeniedHandler;

  public SecurityConfig(
      SupabaseJwtAuthenticationFilter supabaseJwtFilter,
      JsonAuthenticationEntryPoint authenticationEntryPoint,
      JsonAccessDeniedHandler accessDeniedHandler) {
    this.supabaseJwtFilter = supabaseJwtFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // Dev token mint endpoint must be reachable unauthenticated. Safe in prod: its
                    // handler (DevTokenController) is @Profile("local") only, so /dev/** 404s in
                    // prod regardless of this permitAll.
                    .requestMatchers("/dev/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(supabaseJwtFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }
}
