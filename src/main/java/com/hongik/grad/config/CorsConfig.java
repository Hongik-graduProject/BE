package com.hongik.grad.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS whitelist for the mobile/web clients.
 *
 * <p>No wildcard {@code *} origin: only Vercel-hosted frontends and local dev. Supabase domains do
 * not need to be listed here because the browser talks to Supabase directly, not via our backend.
 */
@Configuration
public class CorsConfig {

  private static final List<String> ALLOWED_ORIGIN_PATTERNS =
      List.of("https://*.vercel.app", "http://localhost:*", "http://127.0.0.1:*");

  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowCredentials(true);
    config.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    config.setExposedHeaders(List.of("Content-Type"));
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return new CorsFilter(source);
  }
}
