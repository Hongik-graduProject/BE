package com.hongik.grad.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * LOCAL DEV ONLY — never enabled in prod.
 *
 * <p>Thin proxy over Supabase GoTrue's REST auth endpoints so Swagger can sign a user up / log in
 * without the frontend SDK. Requires {@code supabase.url} and {@code supabase.anon-key} (the public
 * anon key from the dashboard, sent as the {@code apikey} header GoTrue/Kong require).
 */
@Component
@Profile("local")
public class SupabaseAuthClient {

  private final WebClient webClient;

  public SupabaseAuthClient(
      @Value("${supabase.url:}") String url, @Value("${supabase.anon-key:}") String anonKey) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10));
    this.webClient =
        WebClient.builder()
            .baseUrl(url)
            .defaultHeader("apikey", anonKey)
            .defaultHeader("Authorization", "Bearer " + anonKey)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
  }

  /** POST /auth/v1/signup — creates a Supabase user. */
  public JsonNode signUp(String email, String password) {
    return post("/auth/v1/signup", Map.of("email", email, "password", password));
  }

  /** POST /auth/v1/token?grant_type=password — exchanges credentials for an access token. */
  public JsonNode signInWithPassword(String email, String password) {
    return post(
        "/auth/v1/token?grant_type=password", Map.of("email", email, "password", password));
  }

  private JsonNode post(String path, Map<String, String> body) {
    try {
      return webClient
          .post()
          .uri(path)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(JsonNode.class)
          .block();
    } catch (WebClientResponseException ex) {
      throw new DomainException(
          ErrorCode.INVALID_PARAM,
          "Supabase auth failed (" + ex.getStatusCode().value() + "): " + ex.getResponseBodyAsString());
    }
  }
}
