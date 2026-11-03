package com.hongik.grad.stocks.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 네이버 모바일 증권 종목 뉴스 API 비공식 호출. 기존 PC 페이지(news_news.naver)는 2026-10 에 410 으로 종료됐다.
 *
 * <p>응답은 묶음 배열이다: {@code [{"total":1,"items":[{"officeName":"MBC","datetime":"202611022006",
 * "titleFull":"...","mobileNewsUrl":"https://n.news.naver.com/...","imageOriginLink":"..."}]}]}
 */
@Component
public class NaverNewsClient {

  private static final Logger log = LoggerFactory.getLogger(NaverNewsClient.class);
  private static final String URL_FMT =
      "https://m.stock.naver.com/api/news/stock/%s?pageSize=20&page=1";
  private static final DateTimeFormatter NAVER_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 종목별 최신 뉴스 1쪽을 가져온다. 실패 시 빈 리스트. */
  public List<CrawledNews> fetchNews(String ticker) {
    try {
      String body =
          Jsoup.connect(String.format(URL_FMT, ticker))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .referrer("https://m.stock.naver.com/")
              .ignoreContentType(true)
              .timeout(5000)
              .execute()
              .body();
      List<CrawledNews> items = new ArrayList<>();
      for (JsonNode group : objectMapper.readTree(body)) {
        for (JsonNode n : group.path("items")) {
          String title = Parser.unescapeEntities(n.path("titleFull").asText(""), false).trim();
          String url = n.path("mobileNewsUrl").asText("");
          if (title.isEmpty() || url.isEmpty()) continue;
          String source = n.path("officeName").asText(null);
          String thumbnail = n.path("imageOriginLink").asText(null);
          items.add(
              new CrawledNews(
                  title, source, url, thumbnail, parseDate(n.path("datetime").asText(null))));
        }
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
