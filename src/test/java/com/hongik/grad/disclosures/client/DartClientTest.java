package com.hongik.grad.disclosures.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.stocks.domain.StockRepository;
import io.netty.channel.ChannelOption;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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

class DartClientTest {

  private MockWebServer mockWebServer;
  private StockRepository stockRepository;
  private DartClient dartClient;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    stockRepository = mock(StockRepository.class);
    when(stockRepository.findAllTickers()).thenReturn(List.of("005930"));

    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
            .responseTimeout(Duration.ofSeconds(2));
    WebClient webClient =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .baseUrl(mockWebServer.url("/").toString())
            .build();
    dartClient = new DartClient(webClient, "test-api-key", stockRepository);
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("정상 응답 시 DartListItem 목록을 반환한다")
  void successResponse_returnsItems() throws InterruptedException {
    String body =
        """
        {
          "status": "000",
          "message": "정상",
          "page_no": 1,
          "page_count": 100,
          "total_count": 1,
          "total_page": 1,
          "list": [
            {
              "corp_code": "00126380",
              "corp_name": "삼성전자",
              "stock_code": "005930",
              "report_nm": "주요사항보고서",
              "rcept_no": "20240101000001",
              "flr_nm": "삼성전자",
              "rcept_dt": "20240101",
              "rm": ""
            }
          ]
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).corpName()).isEqualTo("삼성전자");
    assertThat(items.get(0).stockCode()).isEqualTo("005930");
    assertThat(items.get(0).rceptNo()).isEqualTo("20240101000001");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getPath()).contains("crtfc_key=test-api-key");
    assertThat(request.getPath()).contains("bgn_de=20240101");
  }

  @Test
  @DisplayName("status 013(데이터 없음) 시 빈 목록을 반환한다")
  void noDataResponse_returnsEmptyList() {
    String body =
        """
        {
          "status": "013",
          "message": "조회된 데이터가 없습니다.",
          "page_no": 0,
          "page_count": 0,
          "total_count": 0,
          "total_page": 0,
          "list": null
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).isEmpty();
  }

  @Test
  @DisplayName("인증 에러(status != 000, 013) 시 빈 목록을 반환한다")
  void authError_returnsEmptyList() {
    String body =
        """
        {
          "status": "800",
          "message": "등록되지 않은 인증키입니다.",
          "page_no": 0,
          "page_count": 0,
          "total_count": 0,
          "total_page": 0,
          "list": null
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).isEmpty();
  }

  @Test
  @DisplayName("API 키가 비어있으면 API 호출 없이 빈 목록을 반환한다")
  void emptyApiKey_returnsEmptyWithoutApiCall() {
    DartClient noKeyClient =
        new DartClient(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            "",
            stockRepository);

    List<DartListItem> items = noKeyClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).isEmpty();
    assertThat(mockWebServer.getRequestCount()).isZero();
  }

  @Test
  @DisplayName("종목 유니버스에 없는 stockCode는 필터링된다")
  void filtersByStockUniverse() {
    when(stockRepository.findAllTickers()).thenReturn(List.of("005930"));

    String body =
        """
        {
          "status": "000",
          "message": "정상",
          "page_no": 1,
          "page_count": 100,
          "total_count": 2,
          "total_page": 1,
          "list": [
            {
              "corp_code": "00126380",
              "corp_name": "삼성전자",
              "stock_code": "005930",
              "report_nm": "주요사항보고서",
              "rcept_no": "20240101000001",
              "flr_nm": "삼성전자",
              "rcept_dt": "20240101",
              "rm": ""
            },
            {
              "corp_code": "99999999",
              "corp_name": "비상장사",
              "stock_code": "999999",
              "report_nm": "기타보고서",
              "rcept_no": "20240101000002",
              "flr_nm": "비상장사",
              "rcept_dt": "20240101",
              "rm": ""
            }
          ]
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).hasSize(1);
    assertThat(items.get(0).stockCode()).isEqualTo("005930");
  }

  @Test
  @DisplayName("stockCode가 비어있는 항목은 필터링된다")
  void emptyStockCode_isFiltered() {
    String body =
        """
        {
          "status": "000",
          "message": "정상",
          "page_no": 1,
          "page_count": 100,
          "total_count": 1,
          "total_page": 1,
          "list": [
            {
              "corp_code": "00126380",
              "corp_name": "비상장법인",
              "stock_code": "",
              "report_nm": "주요사항보고서",
              "rcept_no": "20240101000003",
              "flr_nm": "비상장법인",
              "rcept_dt": "20240101",
              "rm": ""
            }
          ]
        }
        """;
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json"));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).isEmpty();
  }

  @Test
  @DisplayName("서버 에러(500) 시 재시도 후 빈 목록을 반환한다")
  void serverError_returnsEmptyAfterRetry() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));
    mockWebServer.enqueue(new MockResponse().setResponseCode(500));

    List<DartListItem> items = dartClient.fetchDisclosures(LocalDate.of(2024, 1, 1), 100);

    assertThat(items).isEmpty();
  }
}
