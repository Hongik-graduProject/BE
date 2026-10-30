package com.hongik.grad.auth.filter;

import com.hongik.grad.auth.principal.AuthUser;
import com.hongik.grad.auth.provider.JwtClaims;
import com.hongik.grad.auth.provider.JwtVerifier;
import com.hongik.grad.common.exception.DomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves {@code Authorization: Bearer <jwt>} headers into a {@link AuthUser} principal.
 *
 * <p>The filter is intentionally silent when the header is missing or verification fails — the
 * {@code SecurityContext} stays empty and downstream Spring Security entrypoints emit the 401. This
 * keeps response shaping in a single place ({@code JsonAuthenticationEntryPoint}).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  public static final String AUTH_ERROR_ATTRIBUTE = "authError";

  private final JwtVerifier verifier;

  public JwtAuthenticationFilter(JwtVerifier verifier) {
    this.verifier = verifier;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    try {
      JwtClaims claims = verifier.verify(token);
      AuthUser principal = new AuthUser(claims.sub(), claims.email());
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
      SecurityContextHolder.getContext().setAuthentication(auth);
    } catch (DomainException ex) {
      log.debug("Rejected bearer token: {}", ex.code());
      SecurityContextHolder.clearContext();
      request.setAttribute(AUTH_ERROR_ATTRIBUTE, ex);
    }
    chain.doFilter(request, response);
  }
}
