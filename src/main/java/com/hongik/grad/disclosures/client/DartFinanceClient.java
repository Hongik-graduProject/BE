package com.hongik.grad.disclosures.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DART 단일회사 전체 재무제표 API. {@code reprt_code} 11011=사업보고서, 11013=1Q, 11012=반기, 11014=3Q. {@code
 * fs_div} CFS=연결, OFS=별도.
 */
@Component
public class DartFinanceClient {

  private static final Logger log = LoggerFactory.getLogger(DartFinanceClient.class);
  private static final String URL =
      "https://opendart.fss.or.kr/api/fnlttSinglAcntAll.json"
          + "?crtfc_key=%s&corp_code=%s&bsns_year=%d&reprt_code=%s&fs_div=%s";

  private final String apiKey;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DartFinanceClient(@Value("${dart.api.key:}") String apiKey) {
    this.apiKey = apiKey;
  }

  public List<FinanceRow> fetch(String corpCode, int bsnsYear, String reprtCode, String fsDiv) {
    if (apiKey == null || apiKey.isBlank()) return List.of();
    String url = String.format(URL, apiKey, corpCode, bsnsYear, reprtCode, fsDiv);
    try {
      String body =
          Jsoup.connect(url)
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .ignoreContentType(true)
              .maxBodySize(0)
              .timeout(10_000)
              .execute()
              .body();
      JsonNode root = objectMapper.readTree(body);
      String status = root.path("status").asText();
      if (!"000".equals(status)) {
        // 013: 데이터 없음 — 정상 케이스
        if (!"013".equals(status)) {
          log.debug(
              "DART finance status={} corp={} year={} reprt={}",
              status,
              corpCode,
              bsnsYear,
              reprtCode);
        }
        return List.of();
      }
      List<FinanceRow> rows = new ArrayList<>();
      for (JsonNode n : root.path("list")) {
        rows.add(
            new FinanceRow(
                n.path("account_nm").asText(""),
                n.path("thstrm_amount").asText(""),
                n.path("sj_div").asText(""),
                n.path("rcept_no").asText("")));
      }
      return rows;
    } catch (Exception ex) {
      log.warn("DART finance fetch failed for {} {}: {}", corpCode, bsnsYear, ex.getMessage());
      return List.of();
    }
  }

  /**
   * sjDiv = 재무제표 구분 (BS·IS·CIS·CF·SCE). 연결/별도(fs_div)는 요청 파라미터라 행에 없다. rceptNo = 접수번호, 앞 8 자리가
   * 접수일(yyyyMMdd).
   */
  public record FinanceRow(String accountNm, String amount, String sjDiv, String rceptNo) {}
}
