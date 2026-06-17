package com.hongik.grad.dev;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

/**
 * Thin proxy over Supabase GoTrue's REST auth endpoints.
 *
 * <p>Public auth flows (signup/login/refresh/logout) use the {@code anon} key. Admin flows (email
 * lookup, account deletion) use the {@code service_role} key, which is privileged and MUST never be
 * logged or returned to clients. Requires {@code supabase.url}, {@code supabase.anon-key}, and
 * {@code supabase.service-role-key}.
 */
@Component
public class SupabaseAuthClient {

  private static final Logger log = LoggerFactory.getLogger(SupabaseAuthClient.class);

  private final WebClient anonClient;
  private final WebClient adminClient;

  public SupabaseAuthClient(
      @Value("${supabase.url:}") String url,
      @Value("${supabase.anon-key:}") String anonKey,
      @Value("${supabase.service-role-key:}") String serviceRoleKey) {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10));
    ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
    this.anonClient =
        WebClient.builder()
            .baseUrl(url)
            .defaultHeader("apikey", anonKey)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + anonKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(connector)
            .build();
    this.adminClient =
        WebClient.builder()
            .baseUrl(url)
            .defaultHeader("apikey", serviceRoleKey)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(connector)
            .build();
  }

  /** POST /auth/v1/signup — creates a Supabase user. nickname stored in user_metadata when set. */
  public JsonNode signUp(String email, String password, String nickname) {
    Map<String, Object> body = new HashMap<>();
    body.put("email", email);
    body.put("password", password);
    if (nickname != null && !nickname.isBlank()) {
      body.put("data", Map.of("nickname", nickname));
    }
    return postAnon("/auth/v1/signup", body, ErrorCode.DUPLICATE);
  }

  /** POST /auth/v1/token?grant_type=password — exchanges credentials for a session. */
  public JsonNode signInWithPassword(String email, String password) {
    return postAnon(
        "/auth/v1/token?grant_type=password",
        Map.of("email", email, "password", password),
        ErrorCode.AUTH_INVALID);
  }

  /** POST /auth/v1/token?grant_type=refresh_token — rotates a session from a refresh token. */
  public JsonNode refresh(String refreshToken) {
    return postAnon(
        "/auth/v1/token?grant_type=refresh_token",
        Map.of("refresh_token", refreshToken),
        ErrorCode.AUTH_INVALID);
  }

  /** POST /auth/v1/logout — revokes the session for the given user access token. Best-effort. */
  public void logout(String accessToken) {
    anonClient
        .post()
        .uri("/auth/v1/logout")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity()
        .block();
  }

  /** GET /auth/v1/admin/users?email= — service_role only. Returns true if a user exists. */
  public boolean emailExists(String email) {
    JsonNode response =
        adminClient
            .get()
            .uri(uri -> uri.path("/auth/v1/admin/users").queryParam("email", email).build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    if (response == null) {
      return false;
    }
    JsonNode users = response.path("users");
    if (!users.isArray()) {
      return false;
    }
    for (JsonNode user : users) {
      if (email.equalsIgnoreCase(user.path("email").asText())) {
        return true;
      }
    }
    return false;
  }

  /** DELETE /auth/v1/admin/users/{id} — service_role only. Removes the Supabase auth user. */
  public void deleteUser(String userId) {
    adminClient
        .delete()
        .uri("/auth/v1/admin/users/{id}", userId)
        .retrieve()
        .toBodilessEntity()
        .block();
  }

  private JsonNode postAnon(String path, Map<String, ?> body, ErrorCode failureCode) {
    try {
      return anonClient
          .post()
          .uri(path)
          .bodyValue(body)
          .retrieve()
          .bodyToMono(JsonNode.class)
          .block();
    } catch (WebClientResponseException ex) {
      log.debug("GoTrue {} error body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
      throw new DomainException(failureCode, safeMessage(failureCode));
    }
  }

  /** Fixed client-facing message per code; the raw GoTrue body stays in debug logs only. */
  private static String safeMessage(ErrorCode failureCode) {
    return switch (failureCode) {
      case DUPLICATE -> "이미 가입된 이메일입니다";
      case AUTH_INVALID -> "이메일 또는 비밀번호가 올바르지 않습니다";
      default -> "인증 처리에 실패했습니다";
    };
  }
}
