package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 모바일 증권 일별 시세 API 비공식 호출. 최신순. 기존 PC 페이지(sise_day.naver)는 2026-10 에 410 으로 종료됐다.
 *
 * <p>응답 예시: {@code [{"localTradedAt":"2026-11-02","closePrice":"230,000",
 * "compareToPreviousClosePrice":"5,000","openPrice":"220,500","highPrice":"232,000",
 * "lowPrice":"214,000","accumulatedTradingVolume":24083880}]} — 전일비는 부호가 붙어 온다.
 */
@Component
public class NaverDailyPriceClient {

  private static final Logger log = LoggerFactory.getLogger(NaverDailyPriceClient.class);
  private static final String STOCK_URL_FMT =
      "https://m.stock.naver.com/api/stock/%s/price?pageSize=%d&page=%d";
  // shortcut: 종목 시세와 같은 응답 형식이라고 가정했다 (미검증). 형식이 다르면 빈 목록 + WARN. 실제 응답을 확인하면 이 주석을 지운다.
  private static final String KOSPI_URL_FMT =
      "https://m.stock.naver.com/api/index/KOSPI/price?pageSize=%d&page=%d";
  // pageSize 61 이상은 400 을 돌려준다 (2026-11 확인).
  private static final int MAX_PAGE_SIZE = 60;
  // 지수는 소수 둘째 자리까지 온다 ("2,601.53"). BIGINT 컬럼에 담으려고 0.01pt 단위 정수로 바꾼다.
  private static final int INDEX_SCALE = 2;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 최근 {@code days} 거래일의 일봉을 최신→과거 순으로 반환. 60개씩 페이지를 넘겨 모은다. */
  public List<DailyPrice> fetch(String ticker, int days) {
    return fetchSeries(STOCK_URL_FMT.replace("%s", ticker), 0, days);
  }

  /** KOSPI 지수 최근 {@code days} 거래일, 최신→과거 순. 가격은 0.01pt 단위 정수다. */
  public List<DailyPrice> fetchKospi(int days) {
    return fetchSeries(KOSPI_URL_FMT, INDEX_SCALE, days);
  }

  private List<DailyPrice> fetchSeries(String urlFmt, int scale, int days) {
    List<DailyPrice> all = new ArrayList<>();
    int pageSize = Math.min(days, MAX_PAGE_SIZE);
    for (int page = 1; all.size() < days; page++) {
      int before = all.size();
      all.addAll(fetchPage(String.format(urlFmt, pageSize, page), scale));
      if (all.size() - before < pageSize) break; // 마지막 페이지 또는 실패
    }
    return all.size() > days ? all.subList(0, days) : all;
  }

  private List<DailyPrice> fetchPage(String url, int scale) {
    try {
      String body =
          Jsoup.connect(url)
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(5000)
              .execute()
              .body();
      return parse(body, scale);
    } catch (Exception ex) {
      log.warn("Naver daily price fetch failed for {}: {}", url, ex.getMessage());
      return List.of();
    }
  }

  /** 배열이 아니면 형식이 바뀐 것으로 보고 빈 목록을 돌려준다. 날짜가 깨진 행은 건너뛴다. */
  List<DailyPrice> parse(String body, int scale) throws Exception {
    JsonNode root = objectMapper.readTree(body);
    if (root == null || !root.isArray()) {
      log.warn("Naver daily price: unexpected response shape (not an array)");
      return List.of();
    }
    List<DailyPrice> rows = new ArrayList<>();
    for (JsonNode row : root) {
      LocalDate date;
      try {
        date = LocalDate.parse(row.path("localTradedAt").asText(""));
      } catch (Exception e) {
        continue;
      }
      rows.add(
          new DailyPrice(
              date,
              parseLong(row.path("closePrice").asText(""), scale),
              parseLong(row.path("compareToPreviousClosePrice").asText(""), scale),
              parseLong(row.path("openPrice").asText(""), scale),
              parseLong(row.path("highPrice").asText(""), scale),
              parseLong(row.path("lowPrice").asText(""), scale),
              row.path("accumulatedTradingVolume").asLong(0)));
    }
    return rows;
  }

  private static long parseLong(String v, int scale) {
    if (v == null || v.isBlank() || "-".equals(v)) return 0;
    try {
      return new BigDecimal(v.replace(",", "").replace(" ", "").trim())
          .movePointRight(scale)
          .longValue();
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public record DailyPrice(
      LocalDate date, long close, long change, long open, long high, long low, long volume) {}
}
