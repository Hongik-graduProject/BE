package com.hongik.grad.disclosures.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.event.DisclosureCreatedEvent;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test-db")
@Import({TestcontainersConfig.class, DartPollerIntegrationTest.EventCapture.class})
class DartPollerIntegrationTest {

  static MockWebServer mockWebServer;

  @Autowired private DartPoller dartPoller;
  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private EventCapture eventCapture;

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    try {
      mockWebServer = new MockWebServer();
      mockWebServer.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    registry.add("dart.api.key", () -> "test-api-key");
    registry.add("dart.api.base-url", () -> mockWebServer.url("/").toString());
  }

  @BeforeEach
  void setUp() {
    disclosureRepository.deleteAll();
    eventCapture.clear();
    if (!stockRepository.existsById("005930")) {
      stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
    }
    if (!stockRepository.existsById("000660")) {
      stockRepository.save(new Stock("000660", "SK하이닉스", Market.KOSPI));
    }
  }

  @AfterAll
  static void tearDown() throws IOException {
    if (mockWebServer != null) {
      mockWebServer.shutdown();
    }
  }

  @Test
  @DisplayName("DART 응답에서 등록된 종목의 공시만 저장하고 이벤트를 발행한다")
  void pollSavesMatchingDisclosuresAndPublishesEvents() {
    String dartResponse =
        """
        {
          "status": "000",
          "message": "정상",
          "page_no": 1,
          "page_count": 100,
          "total_count": 3,
          "total_page": 1,
          "list": [
            {
              "corp_code": "00126380",
              "corp_name": "삼성전자",
              "stock_code": "005930",
              "report_nm": "사업보고서 (2024.12)",
              "rcept_no": "20250401000001",
              "flr_nm": "삼성전자",
              "rcept_dt": "20250401",
              "rm": ""
            },
            {
              "corp_code": "00164779",
              "corp_name": "SK하이닉스",
              "stock_code": "000660",
              "report_nm": "분기보고서 (2025.03)",
              "rcept_no": "20250401000002",
              "flr_nm": "SK하이닉스",
              "rcept_dt": "20250401",
              "rm": ""
            },
            {
              "corp_code": "00999999",
              "corp_name": "미등록회사",
              "stock_code": "999999",
              "report_nm": "사업보고서",
              "rcept_no": "20250401000003",
              "flr_nm": "미등록회사",
              "rcept_dt": "20250401",
              "rm": ""
            }
          ]
        }
        """;
    mockWebServer.enqueue(
        new MockResponse().addHeader("Content-Type", "application/json").setBody(dartResponse));

    dartPoller.poll();

    List<Disclosure> all = disclosureRepository.findAll();
    assertThat(all).hasSize(2);
    assertThat(all)
        .extracting(d -> d.getStock().getTicker())
        .containsExactlyInAnyOrder("005930", "000660");
    assertThat(all)
        .extracting(Disclosure::getDartFilingId)
        .containsExactlyInAnyOrder("20250401000001", "20250401000002");

    assertThat(eventCapture.getEvents()).hasSize(2);
    assertThat(eventCapture.getEvents())
        .extracting(DisclosureCreatedEvent::ticker)
        .containsExactlyInAnyOrder("005930", "000660");
  }

  @Test
  @DisplayName("동일한 dart_filing_id로 재폴링 시 중복 저장되지 않는다")
  void pollSkipsDuplicates() {
    String dartResponse =
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
              "report_nm": "반기보고서 (2025.06)",
              "rcept_no": "20250701000001",
              "flr_nm": "삼성전자",
              "rcept_dt": "20250701",
              "rm": ""
            }
          ]
        }
        """;
    mockWebServer.enqueue(
        new MockResponse().addHeader("Content-Type", "application/json").setBody(dartResponse));
    mockWebServer.enqueue(
        new MockResponse().addHeader("Content-Type", "application/json").setBody(dartResponse));

    dartPoller.poll();
    assertThat(disclosureRepository.findAll()).hasSize(1);
    assertThat(eventCapture.getEvents()).hasSize(1);

    eventCapture.clear();
    dartPoller.poll();
    assertThat(disclosureRepository.findAll()).hasSize(1);
    assertThat(eventCapture.getEvents()).isEmpty();
  }

  @TestConfiguration
  static class EventCapture {

    private final List<DisclosureCreatedEvent> events = new ArrayList<>();

    @EventListener
    void onDisclosureCreated(DisclosureCreatedEvent event) {
      events.add(event);
    }

    List<DisclosureCreatedEvent> getEvents() {
      return events;
    }

    void clear() {
      events.clear();
    }
  }
}
