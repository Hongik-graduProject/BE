package com.hongik.grad.stocks.client;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 네이버 금융 종목별 뉴스 페이지 크롤러. */
@Component
public class NaverNewsClient {

  private static final Logger log = LoggerFactory.getLogger(NaverNewsClient.class);
  private static final String URL_FMT =
      "https://finance.naver.com/item/news_news.naver?code=%s&page=1";
  private static final String REFERER_FMT = "https://finance.naver.com/item/main.naver?code=%s";
  private static final DateTimeFormatter NAVER_DATE =
      DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

  /** 종목별 최신 뉴스 페이지 1쪽을 크롤링. 실패 시 빈 리스트. */
  public List<CrawledNews> fetchNews(String ticker) {
    try {
      Document doc =
          Jsoup.connect(String.format(URL_FMT, ticker))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .referrer(String.format(REFERER_FMT, ticker))
              .timeout(5000)
              .get();
      List<CrawledNews> items = new ArrayList<>();
      for (Element row : doc.select("table.type5 tbody tr")) {
        // 관련 뉴스(연관 묶음) 헤더/리스트는 제외
        if (row.hasClass("relation_tit") || row.hasClass("relation_lst")) continue;
        Element titleA = row.selectFirst("td.title a.tit");
        if (titleA == null) titleA = row.selectFirst("td.title a");
        if (titleA == null) continue;

        String title = titleA.text().trim();
        if (title.isEmpty()) continue;
        String url = titleA.absUrl("href");
        if (url.isEmpty()) url = titleA.attr("href");

        Element src = row.selectFirst("td.info");
        Element date = row.selectFirst("td.date");
        String source = src == null ? null : src.text().trim();
        OffsetDateTime publishedAt = parseDate(date == null ? null : date.text().trim());

        items.add(new CrawledNews(title, source, url, null, publishedAt));
      }
      return items;
    } catch (Exception ex) {
      log.warn("Naver news crawl failed for {}: {}", ticker, ex.getMessage());
      return List.of();
    }
  }

  private static OffsetDateTime parseDate(String raw) {
    if (raw == null || raw.isEmpty()) return OffsetDateTime.now();
    try {
      LocalDateTime ldt = LocalDateTime.parse(raw, NAVER_DATE);
      return ldt.atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    } catch (Exception e) {
      return OffsetDateTime.now();
    }
  }

  public record CrawledNews(
      String title, String source, String url, String thumbnailUrl, OffsetDateTime publishedAt) {}
}
