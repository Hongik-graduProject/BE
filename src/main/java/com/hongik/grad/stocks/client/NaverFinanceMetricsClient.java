package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 모바일 증권 종목 종합 API 에서 PER/PBR/EPS/배당수익률·시가총액·52주 최고/최저 조회. 기존 PC 메인 페이지(main.naver)는 2026-10 에
 * stock.naver.com 으로 리다이렉트되며 표가 사라졌다.
 *
 * <p>응답의 {@code totalInfos} 예시: {@code
 * [{"code":"per","value":"10.32배"},{"code":"eps","value":"22,292원"},
 * {"code":"pbr","value":"2.67배"},{"code":"dividendYieldRatio","value":"0.73%"},
 * {"code":"marketValue","value":"1,344조 6,441억"},{"code":"highPriceOf52Weeks","value":"380,000"}]}
 */
@Component
public class NaverFinanceMetricsClient {

  private static final Logger log = LoggerFactory.getLogger(NaverFinanceMetricsClient.class);
  private static final String URL_FMT = "https://m.stock.naver.com/api/stock/%s/integration";
  private static final Pattern JO = Pattern.compile("([\\d,]+)조");
  private static final Pattern EOK = Pattern.compile("([\\d,]+)억");

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Metrics fetch(String ticker) {
    try {
      String body =
          Jsoup.connect(String.format(URL_FMT, ticker))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(5000)
              .execute()
              .body();
      return parse(objectMapper.readTree(body).path("totalInfos"));
    } catch (Exception ex) {
      log.warn("Naver finance metrics fetch failed for {}: {}", ticker, ex.getMessage());
      return Metrics.empty();
    }
  }

  static Metrics parse(JsonNode infos) {
    return new Metrics(
        findValue(infos, "per"),
        findValue(infos, "pbr"),
        toLong(findValue(infos, "eps")),
        findValue(infos, "dividendYieldRatio"),
        parseKrw(findText(infos, "marketValue")),
        toLong(findValue(infos, "highPriceOf52Weeks")),
        toLong(findValue(infos, "lowPriceOf52Weeks")));
  }

  /** "1,344조 6,441억" → 1344_6441_0000_0000 원. 조·억이 모두 없으면 null. */
  static Long parseKrw(String txt) {
    if (txt == null) return null;
    Matcher jo = JO.matcher(txt);
    Matcher eok = EOK.matcher(txt);
    boolean hasJo = jo.find();
    boolean hasEok = eok.find();
    if (!hasJo && !hasEok) return null;
    long won = 0;
    if (hasJo) won += Long.parseLong(jo.group(1).replace(",", "")) * 1_000_000_000_000L;
    if (hasEok) won += Long.parseLong(eok.group(1).replace(",", "")) * 100_000_000L;
    return won;
  }

  private static Long toLong(BigDecimal v) {
    return v == null ? null : v.toBigInteger().longValueExact();
  }

  private static String findText(JsonNode infos, String code) {
    for (JsonNode info : infos) {
      if (code.equals(info.path("code").asText())) return info.path("value").asText("");
    }
    return null;
  }

  /** code 가 일치하는 항목의 값에서 숫자만 뽑는다 ("10.32배" → 10.32, "N/A" → null). */
  private static BigDecimal findValue(JsonNode infos, String code) {
    for (JsonNode info : infos) {
      if (!code.equals(info.path("code").asText())) continue;
      String txt = info.path("value").asText("").replace(",", "").replaceAll("[^0-9.\\-]", "");
      if (txt.isEmpty() || ".".equals(txt) || "-".equals(txt)) return null;
      try {
        return new BigDecimal(txt).setScale(2, RoundingMode.HALF_UP);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  public record Metrics(
      BigDecimal per,
      BigDecimal pbr,
      Long eps,
      BigDecimal dividendYield,
      Long marketCap,
      Long high52w,
      Long low52w) {
    public static Metrics empty() {
      return new Metrics(null, null, null, null, null, null, null);
    }
  }
}
