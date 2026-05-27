package com.hongik.grad.stocks.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.stocks.dto.KrxQuote;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

class KrxClientTest {

  private MockWebServer mockWebServer;
  private KrxClient krxClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    String baseUrl = mockWebServer.url("").toString();
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }

    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
            .responseTimeout(Duration.ofSeconds(2));
    WebClient.Builder builder =
        WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    krxClient = new KrxClient(builder, baseUrl, "test-key", "test-secret");
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("정상 응답 시 KrxQuote를 올바르게 파싱한다")
  void successResponse_returnsQuote() {
    String responseBody =
        """
        {
          "output": {
            "stck_shrn_iscd": "005930",
            "stck_prpr": "72000",
            "prdy_ctrt": "1.50",
            "prdy_vrss": "1000"
          }
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));

    KrxQuote quote = krxClient.getQuote("005930");

    assertThat(quote.ticker()).isEqualTo("005930");
    assertThat(quote.currentPrice()).isEqualTo(72000L);
    assertThat(quote.changeRate()).isEqualTo(1.50);
    assertThat(quote.changeAmount()).isEqualTo(1000L);
  }

  @Test
  @DisplayName("429 응답 후 재시도하여 성공하면 올바른 결과를 반환한다")
  void rateLimited_retryThenSuccess() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(429));

    String responseBody =
        """
        {
          "output": {
            "stck_shrn_iscd": "005930",
            "stck_prpr": "72000",
            "prdy_ctrt": "1.50",
            "prdy_vrss": "1000"
          }
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));

    KrxQuote quote = krxClient.getQuote("005930");

    assertThat(quote.ticker()).isEqualTo("005930");
    assertThat(quote.currentPrice()).isEqualTo(72000L);
  }

  @Test
  @DisplayName("500 서버 에러 시 빈 KrxQuote를 반환한다")
  void serverError_returnsEmpty() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    KrxQuote quote = krxClient.getQuote("005930");

    assertThat(quote.ticker()).isEqualTo("005930");
    assertThat(quote.currentPrice()).isZero();
    assertThat(quote.changeRate()).isZero();
    assertThat(quote.changeAmount()).isZero();
  }

  @Test
  @DisplayName("타임아웃 시 빈 KrxQuote를 반환한다")
  void timeout_returnsEmpty() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody("{}")
            .addHeader("Content-Type", "application/json")
            .setBodyDelay(5, TimeUnit.SECONDS));

    KrxQuote quote = krxClient.getQuote("005930");

    assertThat(quote.ticker()).isEqualTo("005930");
    assertThat(quote.currentPrice()).isZero();
  }

  @Test
  @DisplayName("baseUrl이 비어있으면 API 호출 없이 빈 KrxQuote를 반환한다")
  void emptyBaseUrl_returnsEmptyWithoutApiCall() {
    KrxClient noUrlClient = new KrxClient(WebClient.builder(), "", "key", "secret");

    KrxQuote quote = noUrlClient.getQuote("005930");

    assertThat(quote.ticker()).isEqualTo("005930");
    assertThat(quote.currentPrice()).isZero();
  }
}
