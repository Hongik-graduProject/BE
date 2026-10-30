package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.stocks.dto.KrxQuote;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 금융 실시간 시세 폴링 API 비공식 호출. 인증 불필요.
 *
 * <p>응답 예시:
 *
 * <pre>
 * {
 *   "datas": [{
 *     "itemCode": "005930",
 *     "closePrice": "343,000",
 *     "compareToPreviousClosePrice": "6,000",
 *     "compareToPreviousPrice": {"code": "2", "name": "RISING"},
 *     "fluctuationsRatio": "1.78"
 *   }]
 * }
 * </pre>
 */
@Component
public class NaverQuoteClient {

  private static final Logger log = LoggerFactory.getLogger(NaverQuoteClient.class);
  private static final String URL_FMT =
      "https://polling.finance.naver.com/api/realtime/domestic/stock/%s";

  private final ObjectMapper objectMapper = new ObjectMapper();

  public KrxQuote getQuote(String ticker) {
    try {
      String body =
          Jsoup.connect(String.format(URL_FMT, ticker))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(4000)
              .execute()
              .body();
      JsonNode root = objectMapper.readTree(body);
      JsonNode datas = root.path("datas");
      if (!datas.isArray() || datas.isEmpty()) {
        return KrxQuote.empty(ticker);
      }
      JsonNode q = datas.get(0);
      long close = parseLongComma(q.path("closePrice").asText(""));
      long delta = parseLongComma(q.path("compareToPreviousClosePrice").asText(""));
      double rate = parseDoubleComma(q.path("fluctuationsRatio").asText(""));
      String code = q.path("compareToPreviousPrice").path("code").asText("");
      // code 5 = FALLING (하락), 2 = RISING (상승). 다른 코드는 보합·하한가 등.
      boolean falling = "5".equals(code) || "4".equals(code);
      if (falling) {
        delta = -Math.abs(delta);
        rate = -Math.abs(rate);
      }
      return new KrxQuote(ticker, close, rate, delta);
    } catch (Exception ex) {
      log.warn("Naver quote fetch failed for {}: {}", ticker, ex.getMessage());
      return KrxQuote.empty(ticker);
    }
  }

  private static long parseLongComma(String v) {
    if (v == null || v.isBlank()) return 0;
    try {
      return Long.parseLong(v.replace(",", "").trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double parseDoubleComma(String v) {
    if (v == null || v.isBlank()) return 0.0;
    try {
      return Double.parseDouble(v.replace(",", "").trim());
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }
}
