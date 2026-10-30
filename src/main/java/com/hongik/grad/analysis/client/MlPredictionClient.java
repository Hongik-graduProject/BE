package com.hongik.grad.analysis.client;

import com.hongik.grad.analysis.client.dto.MlPredictionJson;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Reads the KOSPI excess-return forecast from the ML model service (the FastAPI app in the ML
 * repository, {@code boseong} branch). The backend only consumes the stored-prediction endpoint —
 * per BACKEND_HANDOFF.md §2 it must not build the model's 31 features itself.
 *
 * <p>Every failure degrades to {@link Optional#empty()} rather than throwing: the service is
 * optional infrastructure, most tickers are outside the model universe (404), and the model returns
 * 503 until its artifacts are loaded. None of those may break the analysis screen.
 */
@Component
public class MlPredictionClient {

  private static final Logger log = LoggerFactory.getLogger(MlPredictionClient.class);
  private static final String API_KEY_HEADER = "X-API-Key";

  /** Null when {@code ml.api.base-url} is unset — the service is then treated as absent. */
  private final WebClient webClient;

  private final String apiKey;

  @Autowired
  public MlPredictionClient(
      WebClient.Builder webClientBuilder,
      @Value("${ml.api.base-url:}") String baseUrl,
      @Value("${ml.api.key:}") String apiKey) {
    // clone() so configuring a base URL does not mutate the shared builder bean.
    this.webClient =
        (baseUrl == null || baseUrl.isBlank())
            ? null
            : webClientBuilder.clone().baseUrl(baseUrl).build();
    this.apiKey = apiKey == null ? "" : apiKey;
  }

  MlPredictionClient(WebClient webClient, String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey == null ? "" : apiKey;
  }

  public Optional<MlPredictionJson> fetchPrediction(String ticker) {
    if (webClient == null) {
      log.debug("ml.api.base-url not configured; no prediction for {}", ticker);
      return Optional.empty();
    }
    try {
      MlPredictionJson body =
          webClient
              .get()
              .uri("/api/v1/predictions/{code}", ticker)
              .headers(
                  headers -> {
                    if (!apiKey.isBlank()) {
                      headers.set(API_KEY_HEADER, apiKey);
                    }
                  })
              .retrieve()
              .bodyToMono(MlPredictionJson.class)
              .block();
      return Optional.ofNullable(body);
    } catch (WebClientResponseException.NotFound ex) {
      log.debug("ML model has no prediction for ticker {}", ticker);
      return Optional.empty();
    } catch (Exception ex) {
      log.warn("ML prediction fetch failed for {}: {}", ticker, ex.getMessage());
      return Optional.empty();
    }
  }
}
