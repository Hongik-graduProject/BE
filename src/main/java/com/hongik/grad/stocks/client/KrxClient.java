package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

@Component
public class KrxClient {

  private static final Logger log = LoggerFactory.getLogger(KrxClient.class);

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;
  private final String apiSecret;

  public KrxClient(
      WebClient.Builder webClientBuilder,
      @Value("${krx.api.base-url:}") String baseUrl,
      @Value("${krx.api.key:}") String apiKey,
      @Value("${krx.api.secret:}") String apiSecret) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.webClient = webClientBuilder.build();
  }

  public KrxQuote getQuote(String ticker) {
    if (baseUrl == null || baseUrl.isBlank()) {
      log.debug("KRX API base URL not configured; returning empty quote for {}", ticker);
      return KrxQuote.empty(ticker);
    }

    try {
      KrxQuote quote =
          webClient
              .get()
              .uri(
                  baseUrl
                      + "/uapi/domestic-stock/v1/quotations/inquire-price?fid_input_iscd={ticker}",
                  ticker)
              .header("appkey", apiKey)
              .header("appsecret", apiSecret)
              .header("tr_id", "FHKST01010100")
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

  private boolean isRetryable(Throwable throwable) {
    if (throwable instanceof WebClientResponseException responseException) {
      return responseException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
          || responseException.getStatusCode().is5xxServerError();
    }
    return false;
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  private record KrxApiResponse(KrxApiOutput output) {

    KrxQuote toQuote(String ticker) {
      if (output == null) {
        return KrxQuote.empty(ticker);
      }
      return new KrxQuote(
          output.stckShrnIscd(),
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
