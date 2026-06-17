package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

/**
 * 한국투자증권 KIS OpenAPI 클라이언트.
 *
 * <p>흐름: (1) /oauth2/tokenP 로 access_token 발급(24h 캐싱) → (2) Bearer 헤더 + appkey/appsecret/tr_id +
 * FID_COND_MRKT_DIV_CODE=J 로 inquire-price 호출.
 */
@Component
public class KrxClient {

  private static final Logger log = LoggerFactory.getLogger(KrxClient.class);
  private static final String TR_ID_INQUIRE_PRICE = "FHKST01010100";
  /** KIS 토큰 만료 6시간 전에 미리 갱신. 토큰 실제 유효기간은 24h. */
  private static final Duration TOKEN_REFRESH_AHEAD = Duration.ofHours(6);

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;
  private final String apiSecret;
  private final NaverQuoteClient naverQuoteClient;
  private final AtomicReference<CachedToken> token = new AtomicReference<>();

  public KrxClient(
      WebClient.Builder webClientBuilder,
      @Value("${krx.api.base-url:}") String baseUrl,
      @Value("${krx.api.key:}") String apiKey,
      @Value("${krx.api.secret:}") String apiSecret,
      NaverQuoteClient naverQuoteClient) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.naverQuoteClient = naverQuoteClient;
    this.webClient = webClientBuilder.build();
  }

  public KrxQuote getQuote(String ticker) {
    // KIS OPENAPI 키가 없으면 즉시 네이버 금융으로 폴백.
    if (baseUrl == null || baseUrl.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
      return naverQuoteClient.getQuote(ticker);
    }

    try {
      String accessToken = ensureToken();
      if (accessToken == null) return KrxQuote.empty(ticker);

      KrxQuote quote =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .scheme("https")
                          .host(host(baseUrl))
                          .port(port(baseUrl))
                          .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                          .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                          .queryParam("FID_INPUT_ISCD", ticker)
                          .build())
              .header("Authorization", "Bearer " + accessToken)
              .header("appkey", apiKey)
              .header("appsecret", apiSecret)
              .header("tr_id", TR_ID_INQUIRE_PRICE)
              .retrieve()
              .bodyToMono(KrxApiResponse.class)
              .retryWhen(
                  Retry.backoff(1, Duration.ofSeconds(1))
                      .filter(this::isRetryable)
                      .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
              .map(resp -> resp.toQuote(ticker))
              .block();

      return quote != null ? quote : KrxQuote.empty(ticker);
    } catch (Exception ex) {
      log.warn("KRX API call failed for ticker {}: {}", ticker, ex.getMessage());
      return KrxQuote.empty(ticker);
    }
  }

  /** 24h 캐싱된 access_token 반환. 만료 임박 시 새로 발급. */
  private String ensureToken() {
    CachedToken current = token.get();
    if (current != null && Instant.now().isBefore(current.expiresAt.minus(TOKEN_REFRESH_AHEAD))) {
      return current.accessToken;
    }
    synchronized (this) {
      current = token.get();
      if (current != null && Instant.now().isBefore(current.expiresAt.minus(TOKEN_REFRESH_AHEAD))) {
        return current.accessToken;
      }
      try {
        TokenResponse resp =
            webClient
                .post()
                .uri(baseUrl + "/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    Map.of(
                        "grant_type", "client_credentials",
                        "appkey", apiKey,
                        "appsecret", apiSecret))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .block(Duration.ofSeconds(10));
        if (resp == null || resp.accessToken == null) {
          log.warn("KIS oauth2 token endpoint returned empty response");
          return null;
        }
        long ttlSeconds = resp.expiresIn != null ? resp.expiresIn : 86_400L;
        CachedToken fresh =
            new CachedToken(resp.accessToken, Instant.now().plusSeconds(ttlSeconds));
        token.set(fresh);
        log.info("Refreshed KIS access token; expires in {}s", ttlSeconds);
        return fresh.accessToken;
      } catch (Exception ex) {
        log.warn("KIS oauth2 token request failed: {}", ex.getMessage());
        return null;
      }
    }
  }

  private boolean isRetryable(Throwable throwable) {
    if (throwable instanceof WebClientResponseException responseException) {
      return responseException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
          || responseException.getStatusCode().is5xxServerError();
    }
    return false;
  }

  private static String host(String url) {
    String stripped = url.replaceFirst("^https?://", "");
    int colon = stripped.indexOf(':');
    int slash = stripped.indexOf('/');
    int end = stripped.length();
    if (colon >= 0) end = Math.min(end, colon);
    if (slash >= 0) end = Math.min(end, slash);
    return stripped.substring(0, end);
  }

  private static int port(String url) {
    String stripped = url.replaceFirst("^https?://", "");
    int colon = stripped.indexOf(':');
    if (colon < 0) return -1;
    int slash = stripped.indexOf('/', colon);
    String portStr = stripped.substring(colon + 1, slash < 0 ? stripped.length() : slash);
    try {
      return Integer.parseInt(portStr);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private record CachedToken(String accessToken, Instant expiresAt) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  private record TokenResponse(String accessToken, Long expiresIn, String tokenType) {}

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  private record KrxApiResponse(KrxApiOutput output) {

    KrxQuote toQuote(String ticker) {
      if (output == null) {
        return KrxQuote.empty(ticker);
      }
      return new KrxQuote(
          output.stckShrnIscd() == null ? ticker : output.stckShrnIscd(),
          parseLong(output.stckPrpr()),
          parseDouble(output.prdyCtrt()),
          parseLong(output.prdyVrss()));
    }

    private static long parseLong(String value) {
      try {
        return value != null ? Long.parseLong(value.trim()) : 0;
      } catch (NumberFormatException e) {
        return 0;
      }
    }

    private static double parseDouble(String value) {
      try {
        return value != null ? Double.parseDouble(value.trim()) : 0.0;
      } catch (NumberFormatException e) {
        return 0.0;
      }
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  private record KrxApiOutput(
      String stckShrnIscd, String stckPrpr, String prdyCtrt, String prdyVrss) {}
}
