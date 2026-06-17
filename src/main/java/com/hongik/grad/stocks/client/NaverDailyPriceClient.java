package com.hongik.grad.stocks.client;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 네이버 금융 일별 시세 페이지 크롤링. 한 페이지당 10개, 최신순. */
@Component
public class NaverDailyPriceClient {

  private static final Logger log = LoggerFactory.getLogger(NaverDailyPriceClient.class);
  private static final String URL_FMT =
      "https://finance.naver.com/item/sise_day.naver?code=%s&page=%d";
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

  /** 최근 {@code pages * 10} 거래일의 일봉을 최신→과거 순으로 반환. */
  public List<DailyPrice> fetch(String ticker, int pages) {
    List<DailyPrice> all = new ArrayList<>();
    try {
      for (int p = 1; p <= pages; p++) {
        Document doc =
            Jsoup.connect(String.format(URL_FMT, ticker, p))
                .userAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                        + " Chrome/120.0.0.0 Safari/537.36")
                .referrer("https://finance.naver.com/item/main.naver?code=" + ticker)
                .timeout(5000)
                .get();
        for (Element row : doc.select("table.type2 tr")) {
          Elements cells = row.select("td");
          if (cells.size() < 7) continue;
          String dateText = textOrNull(cells.get(0));
          if (dateText == null) continue;
          LocalDate date;
          try {
            date = LocalDate.parse(dateText, DATE_FMT);
          } catch (Exception e) {
            continue;
          }
          long close = parseLong(textOrNull(cells.get(1)));
          long change = parseLong(textOrNull(cells.get(2)));
          if (isFallingRow(cells.get(2))) change = -Math.abs(change);
          long open = parseLong(textOrNull(cells.get(3)));
          long high = parseLong(textOrNull(cells.get(4)));
          long low = parseLong(textOrNull(cells.get(5)));
          long volume = parseLong(textOrNull(cells.get(6)));
          all.add(new DailyPrice(date, close, change, open, high, low, volume));
        }
      }
    } catch (Exception ex) {
      log.warn("Naver daily price fetch failed for {}: {}", ticker, ex.getMessage());
    }
    return all;
  }

  private static String textOrNull(Element cell) {
    String s = cell.text().trim();
    return s.isEmpty() ? null : s;
  }

  /** 전일비 셀에 "하락" 표시가 있으면 음수. */
  private static boolean isFallingRow(Element changeCell) {
    return changeCell.select("em.bu_pdn, em.bu_pdnn").size() > 0;
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
