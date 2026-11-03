package com.hongik.grad.market.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.market.dto.MarketIndexResponse;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 금융 지수 폴링 API 비공식 호출. 응답 모양은 종목 폴링 API({@code NaverQuoteClient})와 같다.
 *
 * <p>실패하면 빈 Optional — 시장 요약 화면이 지수 때문에 깨지지 않게 한다.
 */
@Component
public class NaverIndexClient {

  private static final Logger log = LoggerFactory.getLogger(NaverIndexClient.class);
  private static final String URL_FMT =
      "https://polling.finance.naver.com/api/realtime/domestic/index/%s";

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** code 는 KOSPI 또는 KOSDAQ. */
  public Optional<MarketIndexResponse> fetch(String code, String name) {
    try {
      String body =
          Jsoup.connect(String.format(URL_FMT, code))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(4000)
              .execute()
              .body();
      return parse(code, name, objectMapper.readTree(body));
    } catch (Exception ex) {
      log.warn("Naver index fetch failed for {}: {}", code, ex.getMessage());
      return Optional.empty();
    }
  }

  static Optional<MarketIndexResponse> parse(String code, String name, JsonNode root) {
    JsonNode datas = root.path("datas");
    if (!datas.isArray() || datas.isEmpty()) {
      return Optional.empty();
    }
    JsonNode q = datas.get(0);
    Double value = parseDouble(q.path("closePrice").asText(""));
    if (value == null) {
      return Optional.empty();
    }
    double change = Math.abs(orZero(parseDouble(q.path("compareToPreviousClosePrice").asText(""))));
    double rate = Math.abs(orZero(parseDouble(q.path("fluctuationsRatio").asText(""))));
    // 종목 시세와 같은 규칙: code 5(하락)·4(하한)이면 음수.
    String direction = q.path("compareToPreviousPrice").path("code").asText("");
    if ("5".equals(direction) || "4".equals(direction)) {
      change = -change;
      rate = -rate;
    }
    return Optional.of(new MarketIndexResponse(code, name, value, change, rate));
  }

  private static Double parseDouble(String v) {
    if (v == null || v.isBlank()) return null;
    try {
      return Double.parseDouble(v.replace(",", "").trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static double orZero(Double v) {
    return v == null ? 0.0 : v;
  }
}
