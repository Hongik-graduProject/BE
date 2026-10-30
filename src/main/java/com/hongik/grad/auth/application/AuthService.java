package com.hongik.grad.auth.application;

import com.hongik.grad.auth.dto.AuthUserDto;
import com.hongik.grad.auth.dto.SignupResponse;
import com.hongik.grad.auth.dto.TokenResponse;
import com.hongik.grad.auth.provider.JwtClaims;
import com.hongik.grad.auth.provider.JwtProvider;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-managed auth against the local {@code profiles} table. Passwords are BCrypt hashed and
 * tokens are minted by {@link JwtProvider}; there is no external identity provider.
 *
 * <p>Login failures are deliberately indistinguishable (unknown email and wrong password both give
 * {@code AUTH_INVALID}) so the endpoint does not confirm which emails are registered. {@code
 * /auth/check-email} exposes that on purpose for the signup form.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final ProfileRepository profileRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtProvider jwtProvider;
  private final JwtVerifier jwtVerifier;

  public AuthService(
      ProfileRepository profileRepository,
      PasswordEncoder passwordEncoder,
      JwtProvider jwtProvider,
      JwtVerifier jwtVerifier) {
    this.profileRepository = profileRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtProvider = jwtProvider;
    this.jwtVerifier = jwtVerifier;
  }

  @Transactional
  public SignupResponse signup(String email, String password, String nickname) {
    String normalized = Profile.normalizeEmail(email);
    if (profileRepository.existsByEmail(normalized)) {
      throw new DomainException(ErrorCode.DUPLICATE, "이미 가입된 이메일입니다");
    }
    Profile profile =
        profileRepository.save(
            Profile.signup(
                normalized,
                passwordEncoder.encode(password),
                resolveNickname(nickname, normalized)));
    log.info("Signed up profile id={}", profile.getId());
    return new SignupResponse(
        profile.getId().toString(),
        profile.getEmail(),
        profile.getNickname(),
        profile.getCreatedAt() == null ? null : profile.getCreatedAt().toString());
  }

  @Transactional(readOnly = true)
  public TokenResponse login(String email, String password) {
    Profile profile =
        profileRepository
            .findByEmail(Profile.normalizeEmail(email))
            .orElseThrow(
                () -> new DomainException(ErrorCode.AUTH_INVALID, "이메일 또는 비밀번호가 올바르지 않습니다"));
    if (profile.getPasswordHash() == null
        || !passwordEncoder.matches(password, profile.getPasswordHash())) {
      throw new DomainException(ErrorCode.AUTH_INVALID, "이메일 또는 비밀번호가 올바르지 않습니다");
    }
    return tokens(profile, true);
  }

  /**
   * Issues a fresh token pair from a refresh token. Re-reads the profile so a deleted account can
   * no longer refresh, which is the only revocation path for these stateless refresh tokens.
   */
  @Transactional(readOnly = true)
  public TokenResponse refresh(String refreshToken) {
    JwtClaims claims = jwtVerifier.verifyRefresh(refreshToken);
    Profile profile =
        profileRepository
            .findById(claims.sub())
            .orElseThrow(
                () -> new DomainException(ErrorCode.AUTH_INVALID, "세션이 만료되었습니다. 다시 로그인해 주세요"));
    return tokens(profile, false);
  }

  /**
   * Stateless tokens cannot be revoked server-side, so logout is a no-op here. The client clears
   * its stored tokens, which is what ends the session in practice.
   */
  public void logout(String accessToken) {
    log.debug("Logout requested; stateless tokens expire on their own");
  }

  @Transactional(readOnly = true)
  public boolean isEmailAvailable(String email) {
    return !profileRepository.existsByEmail(Profile.normalizeEmail(email));
  }

  @Transactional
  public void deleteAccount(UUID userId) {
    // FK ON DELETE CASCADE off profiles cleans up folders/favorites/fcm_tokens/notifications.
    profileRepository.deleteById(userId);
  }

  private TokenResponse tokens(Profile profile, boolean includeUser) {
    AuthUserDto user =
        includeUser
            ? new AuthUserDto(profile.getId().toString(), profile.getEmail(), profile.getNickname())
            : null;
    return new TokenResponse(
        jwtProvider.createAccessToken(profile.getId(), profile.getEmail()),
        jwtProvider.createRefreshToken(profile.getId()),
        "Bearer",
        jwtProvider.accessTokenExpiresInSeconds(),
        user);
  }

  /** profiles.nickname is NOT NULL, so fall back to the email local part when none is given. */
  private static String resolveNickname(String nickname, String normalizedEmail) {
    if (nickname != null && !nickname.isBlank()) {
      return nickname.trim();
    }
    int at = normalizedEmail.indexOf('@');
    String local = at > 0 ? normalizedEmail.substring(0, at) : normalizedEmail;
    return local.length() > 50 ? local.substring(0, 50) : local;
  }
}
