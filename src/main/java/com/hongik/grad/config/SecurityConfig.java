package com.hongik.grad.config;

import com.hongik.grad.auth.filter.JwtAuthenticationFilter;
import com.hongik.grad.auth.handler.JsonAccessDeniedHandler;
import com.hongik.grad.auth.handler.JsonAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security chain. Tokens are issued and verified by this backend itself.
 *
 * <p>Every request except health, OpenAPI metadata, and CORS preflight must carry a valid Bearer
 * token verified by {@link JwtAuthenticationFilter}.
 */
@Configuration
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtFilter;
  private final JsonAuthenticationEntryPoint authenticationEntryPoint;
  private final JsonAccessDeniedHandler accessDeniedHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtFilter,
      JsonAuthenticationEntryPoint authenticationEntryPoint,
      JsonAccessDeniedHandler accessDeniedHandler) {
    this.jwtFilter = jwtFilter;
    this.authenticationEntryPoint = authenticationEntryPoint;
    this.accessDeniedHandler = accessDeniedHandler;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        // Wire the CorsConfigurationSource bean into the security chain so CORS headers are present
        // on security-rejected responses (401/403) too — otherwise the browser masks them as opaque
        // network errors and the real auth failure is invisible to the client.
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                // ASYNC/ERROR re-dispatches only follow a REQUEST dispatch that was already
                // authorized; the JWT filter does not re-run on them, so without this an SSE
                // timeout/completion dispatch is denied after the stream is committed.
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // Public auth flows. logout + account stay authenticated (they need the
                    // caller's Bearer token / principal).
                    .requestMatchers(
                        HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/refresh")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/auth/check-email")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler));
    return http.build();
  }

  /** BCrypt for the locally stored password hashes (see {@code profiles.password_hash}). */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
