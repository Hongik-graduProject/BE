package com.hongik.grad.disclosures.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** DART 회사 기본정보 API (/api/company.json) 호출. */
@Component
public class DartCompanyClient {

  private static final Logger log = LoggerFactory.getLogger(DartCompanyClient.class);
  private static final String URL =
      "https://opendart.fss.or.kr/api/company.json?crtfc_key=%s&corp_code=%s";

  private final String apiKey;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DartCompanyClient(@Value("${dart.api.key:}") String apiKey) {
    this.apiKey = apiKey;
  }

  public CompanyInfo fetch(String corpCode) {
    if (apiKey == null || apiKey.isBlank() || corpCode == null || corpCode.isBlank()) {
      return CompanyInfo.empty();
    }
    try {
      String body =
          Jsoup.connect(String.format(URL, apiKey, corpCode))
              .userAgent("Mozilla/5.0")
              .ignoreContentType(true)
              .maxBodySize(0)
              .timeout(8000)
              .execute()
              .body();
      JsonNode root = objectMapper.readTree(body);
      if (!"000".equals(root.path("status").asText())) return CompanyInfo.empty();
      return new CompanyInfo(
          root.path("corp_name").asText(""),
          root.path("ceo_nm").asText(""),
          root.path("est_dt").asText(""), // 설립일 YYYYMMDD
          root.path("induty_code").asText(""), // 업종 코드 (KSIC 5자리)
          root.path("corp_cls").asText("")); // Y=유가증권, K=코스닥
    } catch (Exception ex) {
      log.warn("DART company.json fetch failed for {}: {}", corpCode, ex.getMessage());
      return CompanyInfo.empty();
    }
  }

  public record CompanyInfo(
      String corpName, String ceoName, String establishedDate, String industryCode, String corpClass) {
    public static CompanyInfo empty() {
      return new CompanyInfo("", "", "", "", "");
    }

    public boolean isEmpty() {
      return corpName.isEmpty() && ceoName.isEmpty() && establishedDate.isEmpty();
    }
  }
}
