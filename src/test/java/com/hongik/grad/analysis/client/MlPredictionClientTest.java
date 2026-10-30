package com.hongik.grad.analysis.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.analysis.client.dto.MlPredictionJson;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class MlPredictionClientTest {

  /**
   * Full payload of the model service's LatestPredictionItem. Deliberately complete: the client
   * maps only ten of these fields, so this also pins that unmapped fields do not break decoding.
   */
  private static final String FULL_PAYLOAD =
      """
      {
        "date": "2026-10-02",
        "code": "005930",
        "name": "삼성전자",
        "sector": "반도체",
        "close": 343000.0,
        "predicted_excess_return_60d": 0.0421,
        "predicted_percentile": 0.93,
        "signal": "Top 10%",
        "model_agreement_score": 0.81,
        "model_disagreement": 0.19,
        "scenario_price_if_kospi_flat": 357440.0,
        "avg_trading_value_20": 812000000000.0,
        "feature_coverage": 0.97,
        "disclosure_date": "2026-08-14",
        "financial_age_days": 49.0,
        "financial_source": "DART",
        "macro_as_of_date": "2026-09-30",
        "dna_age_days": 12.0
      }
      """;

  private MockWebServer mockWebServer;
  private MlPredictionClient client;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    client = new MlPredictionClient(webClient(), "secret-key");
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  private WebClient webClient() {
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
            .responseTimeout(Duration.ofSeconds(2));
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(mockWebServer.url("/").toString())
        .build();
  }

  @Test
  @DisplayName("200 응답을 매핑하고 X-API-Key 헤더를 붙인다")
  void mapsSuccessfulResponseAndSendsApiKey() throws InterruptedException {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(FULL_PAYLOAD));

    Optional<MlPredictionJson> result = client.fetchPrediction("005930");

    assertThat(result).isPresent();
    MlPredictionJson prediction = result.orElseThrow();
    assertThat(prediction.code()).isEqualTo("005930");
    assertThat(prediction.date()).isEqualTo(LocalDate.of(2026, 10, 2));
    assertThat(prediction.predictedExcessReturn60d()).isEqualTo(0.0421);
    assertThat(prediction.predictedPercentile()).isEqualTo(0.93);
    assertThat(prediction.signal()).isEqualTo("Top 10%");
    assertThat(prediction.modelAgreementScore()).isEqualTo(0.81);
    assertThat(prediction.scenarioPriceIfKospiFlat()).isEqualTo(357440.0);
    assertThat(prediction.featureCoverage()).isEqualTo(0.97);

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/v1/predictions/005930");
    assertThat(request.getHeader("X-API-Key")).isEqualTo("secret-key");
  }

  @Test
  @DisplayName("404(해당 종목 예측 없음)는 빈 결과로 떨어진다")
  void returnsEmptyOnNotFound() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));

    assertThat(client.fetchPrediction("000660")).isEmpty();
  }

  @Test
  @DisplayName("503(모델 artifact 미로드)도 예외 없이 빈 결과로 떨어진다")
  void returnsEmptyOnServiceUnavailable() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(503));

    assertThat(client.fetchPrediction("005930")).isEmpty();
  }

  @Test
  @DisplayName("base-url 미설정이면 호출 없이 빈 결과를 돌려준다")
  void returnsEmptyWhenBaseUrlUnconfigured() {
    MlPredictionClient unconfigured =
        new MlPredictionClient(WebClient.builder(), "   ", "secret-key");

    assertThat(unconfigured.fetchPrediction("005930")).isEmpty();
    assertThat(mockWebServer.getRequestCount()).isZero();
  }
}
