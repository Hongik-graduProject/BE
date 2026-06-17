package com.hongik.grad.auth.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.hongik.grad.auth.dto.AuthUserDto;
import com.hongik.grad.auth.dto.SignupResponse;
import com.hongik.grad.auth.dto.TokenResponse;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.dev.SupabaseAuthClient;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auth orchestration: proxies Supabase GoTrue for signup/login/refresh/logout and admin
 * (email-existence, account deletion), translating GoTrue JSON into the app's wire DTOs.
 *
 * <p>The service_role key lives only in {@link SupabaseAuthClient}; this layer never logs tokens.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final SupabaseAuthClient supabaseAuthClient;
  private final ProfileRepository profileRepository;

  public AuthService(SupabaseAuthClient supabaseAuthClient, ProfileRepository profileRepository) {
    this.supabaseAuthClient = supabaseAuthClient;
    this.profileRepository = profileRepository;
  }

  public SignupResponse signup(String email, String password, String nickname) {
    JsonNode response = supabaseAuthClient.signUp(email, password, nickname);
    JsonNode user =
        response.has("user") && !response.path("user").isNull() ? response.path("user") : response;
    String id = user.path("id").asText();
    if (id == null || id.isBlank()) {
      throw new DomainException(ErrorCode.INTERNAL_ERROR, "Supabase signup returned no user id");
    }
    return new SignupResponse(
        id,
        user.path("email").asText(email),
        nicknameFrom(user, nickname),
        user.path("created_at").asText(null));
  }

  public TokenResponse login(String email, String password) {
    JsonNode response = supabaseAuthClient.signInWithPassword(email, password);
    return toTokenResponse(response, true);
  }

  public TokenResponse refresh(String refreshToken) {
    JsonNode response = supabaseAuthClient.refresh(refreshToken);
    return toTokenResponse(response, false);
  }

  public void logout(String accessToken) {
    try {
      supabaseAuthClient.logout(accessToken);
    } catch (RuntimeException ex) {
      // Logout is best-effort: the client clears local tokens regardless.
      log.warn("Supabase logout failed (ignored): {}", ex.getMessage());
    }
  }

  public boolean isEmailAvailable(String email) {
    return !supabaseAuthClient.emailExists(email);
  }

  @Transactional
  public void deleteAccount(UUID userId) {
    supabaseAuthClient.deleteUser(userId.toString());
    // FK ON DELETE CASCADE off profiles cleans up folders/favorites/fcm_tokens/notifications.
    profileRepository.deleteById(userId);
  }

  private TokenResponse toTokenResponse(JsonNode response, boolean includeUser) {
    String accessToken = response.path("access_token").asText(null);
    if (accessToken == null || accessToken.isBlank()) {
      throw new DomainException(ErrorCode.AUTH_INVALID, "Supabase returned no access token");
    }
    AuthUserDto user = null;
    if (includeUser && response.has("user") && !response.path("user").isNull()) {
      JsonNode u = response.path("user");
      user =
          new AuthUserDto(u.path("id").asText(), u.path("email").asText(), nicknameFrom(u, null));
    }
    return new TokenResponse(
        accessToken,
        response.path("refresh_token").asText(null),
        response.path("token_type").asText("Bearer"),
        response.path("expires_in").asLong(0),
        user);
  }

  private static String nicknameFrom(JsonNode user, String fallback) {
    JsonNode metadata = user.path("user_metadata");
    JsonNode nickname = metadata.path("nickname");
    if (nickname.isTextual() && !nickname.asText().isBlank()) {
      return nickname.asText();
    }
    return fallback;
  }
}
