package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.stocks.domain.Market;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 모바일 증권 시장별 전 종목 목록 API 비공식 호출. DART corpCode.xml 에는 시장 구분이 없어서 이 목록으로 KOSPI/KOSDAQ 을 나눈다.
 *
 * <p>응답 예시: {@code {"totalCount":2481,"stocks":[{"itemCode":"005930","stockName":"삼성전자"}]}}. 한 쪽 최대
 * 100 개.
 */
@Component
public class NaverMarketListClient {

  private static final Logger log = LoggerFactory.getLogger(NaverMarketListClient.class);
  private static final String URL_FMT =
      "https://m.stock.naver.com/api/stocks/marketValue/%s?page=%d&pageSize=%d";
  private static final int PAGE_SIZE = 100;
  private static final int MAX_PAGES = 60;

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** ticker → 시장과 시가총액. 어느 한 쪽이라도 실패하면 빈 맵을 돌려준다 (일부만 받은 목록으로 종목을 지우지 않도록). */
  public Map<String, Listing> fetchAll() {
    Map<String, Listing> result = new HashMap<>();
    for (Market market : Market.values()) {
      Map<String, Listing> one = fetchMarket(market);
      if (one.isEmpty()) {
        return Map.of();
      }
      result.putAll(one);
    }
    return result;
  }

  private Map<String, Listing> fetchMarket(Market market) {
    Map<String, Listing> result = new HashMap<>();
    try {
      int total = Integer.MAX_VALUE;
      for (int page = 1; page <= MAX_PAGES && result.size() < total; page++) {
        String body =
            Jsoup.connect(String.format(URL_FMT, market.name(), page, PAGE_SIZE))
                .userAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                        + " Chrome/120.0.0.0 Safari/537.36")
                .referrer("https://m.stock.naver.com/")
                .ignoreContentType(true)
                .timeout(5000)
                .execute()
                .body();
        JsonNode root = objectMapper.readTree(body);
        total = root.path("totalCount").asInt(0);
        JsonNode stocks = root.path("stocks");
        if (!stocks.isArray() || stocks.isEmpty()) break;
        for (JsonNode s : stocks) {
          String code = s.path("itemCode").asText("");
          // marketValueRaw: 원 단위 시가총액 (예: "1344644079840000"). 없으면 null.
          long cap = s.path("marketValueRaw").asLong(0);
          if (!code.isEmpty()) result.put(code, new Listing(market, cap > 0 ? cap : null));
        }
      }
      // 조회 중 목록이 조금 바뀔 수 있어 2% 까지는 허용한다.
      if (result.isEmpty() || result.size() < total * 0.98) {
        log.warn("Naver {} list incomplete: {}/{}", market, result.size(), total);
        return Map.of();
      }
      return result;
    } catch (Exception ex) {
      log.warn("Naver {} list fetch failed: {}", market, ex.getMessage());
      return Map.of();
    }
  }

  /** 시장별 목록의 한 종목. marketCap 은 원 단위. */
  public record Listing(Market market, Long marketCap) {}
}
