package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private static final String URL_FMT =
      "https://m.stock.naver.com/api/stock/%s/price?pageSize=%d&page=%d";
  // pageSize 61 이상은 400 을 돌려준다 (2026-11 확인).
  private static final int MAX_PAGE_SIZE = 60;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 최근 {@code days} 거래일의 일봉을 최신→과거 순으로 반환. 60개씩 페이지를 넘겨 모은다. */
  public List<DailyPrice> fetch(String ticker, int days) {
    List<DailyPrice> all = new ArrayList<>();
    int pageSize = Math.min(days, MAX_PAGE_SIZE);
    for (int page = 1; all.size() < days; page++) {
      int before = all.size();
      fetchPage(ticker, pageSize, page, all);
      if (all.size() - before < pageSize) break; // 마지막 페이지 또는 실패
    }
    return all.size() > days ? all.subList(0, days) : all;
  }

  private void fetchPage(String ticker, int pageSize, int page, List<DailyPrice> all) {
    try {
      String body =
          Jsoup.connect(String.format(URL_FMT, ticker, pageSize, page))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(5000)
              .execute()
              .body();
      for (JsonNode row : objectMapper.readTree(body)) {
        LocalDate date;
        try {
          date = LocalDate.parse(row.path("localTradedAt").asText(""));
        } catch (Exception e) {
          continue;
        }
        all.add(
            new DailyPrice(
                date,
                parseLong(row.path("closePrice").asText("")),
                parseLong(row.path("compareToPreviousClosePrice").asText("")),
                parseLong(row.path("openPrice").asText("")),
                parseLong(row.path("highPrice").asText("")),
                parseLong(row.path("lowPrice").asText("")),
                row.path("accumulatedTradingVolume").asLong(0)));
      }
    } catch (Exception ex) {
      log.warn("Naver daily price fetch failed for {}: {}", ticker, ex.getMessage());
    }
  }

  private static long parseLong(String v) {
    if (v == null || v.isBlank() || "-".equals(v)) return 0;
    try {
      return Long.parseLong(v.replace(",", "").replace(" ", "").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public record DailyPrice(
      LocalDate date, long close, long change, long open, long high, long low, long volume) {}
}
