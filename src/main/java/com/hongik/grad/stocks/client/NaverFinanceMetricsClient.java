package com.hongik.grad.stocks.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 네이버 금융 종목 메인 페이지에서 PER/PBR/EPS/배당수익률 등 비율 지표 크롤링. */
@Component
public class NaverFinanceMetricsClient {

  private static final Logger log = LoggerFactory.getLogger(NaverFinanceMetricsClient.class);
  private static final String URL_FMT = "https://finance.naver.com/item/main.naver?code=%s";

  public Metrics fetch(String ticker) {
    try {
      Document doc =
          Jsoup.connect(String.format(URL_FMT, ticker))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .timeout(5000)
              .get();
      return new Metrics(
          findValue(doc, "PER"),
          findValue(doc, "PBR"),
          findLong(doc, "EPS"),
          findValue(doc, "배당수익률"));
    } catch (Exception ex) {
      log.warn("Naver finance metrics fetch failed for {}: {}", ticker, ex.getMessage());
      return Metrics.empty();
    }
  }

  /**
   * 페이지의 종목정보 테이블에서 라벨이 일치하는 값 셀을 찾는다. 네이버는 자주 구조를 바꾸므로
   * 텍스트 기반의 휴리스틱 매칭 (예: "PER" / "PERl" 등) 으로 안전하게 추출.
   */
  private static BigDecimal findValue(Document doc, String label) {
    for (Element em : doc.select("em")) {
      String parent = em.parent() == null ? "" : em.parent().text();
      if (parent != null && parent.contains(label)) {
        String txt = em.text().replace(",", "").replaceAll("[^0-9.\\-]", "").trim();
        if (txt.isEmpty() || ".".equals(txt) || "-".equals(txt)) continue;
        try {
          return new BigDecimal(txt).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return null;
  }

  private static Long findLong(Document doc, String label) {
    BigDecimal v = findValue(doc, label);
    return v == null ? null : v.toBigInteger().longValueExact();
  }

  public record Metrics(BigDecimal per, BigDecimal pbr, Long eps, BigDecimal dividendYield) {
    public static Metrics empty() {
      return new Metrics(null, null, null, null);
    }
  }
}
