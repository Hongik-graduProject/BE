package com.hongik.grad.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS whitelist for the mobile/web clients.
 *
 * <p>No wildcard {@code *} origin: only Vercel-hosted frontends and local dev.
 *
 * <p>Exposed as a {@link CorsConfigurationSource} bean (not a standalone {@code CorsFilter}) so
 * that {@code SecurityConfig} can wire it into the Spring Security filter chain via {@code
 * http.cors()}. This is essential: a standalone {@code CorsFilter} bean runs <em>after</em> the
 * security chain, so security-rejected responses (401/403) would be written without {@code
 * Access-Control-Allow-Origin} and the browser would surface them as opaque network errors. Wiring
 * CORS into the security chain makes the headers present on error responses too.
 */
@Configuration
public class CorsConfig {

  private static final List<String> ALLOWED_ORIGIN_PATTERNS =
      List.of("https://*.vercel.app", "http://localhost:*", "http://127.0.0.1:*");

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    config.setExposedHeaders(List.of("Content-Type"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
