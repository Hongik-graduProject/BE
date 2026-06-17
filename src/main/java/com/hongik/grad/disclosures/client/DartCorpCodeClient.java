package com.hongik.grad.disclosures.client;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** DART corpCode.xml.zip 다운로드 + 파싱. stock_code 있는 항목만 ticker → corp_code 매핑 반환. */
@Component
public class DartCorpCodeClient {

  private static final Logger log = LoggerFactory.getLogger(DartCorpCodeClient.class);
  private static final String URL =
      "https://opendart.fss.or.kr/api/corpCode.xml?crtfc_key=%s";

  private final String apiKey;

  public DartCorpCodeClient(@Value("${dart.api.key:}") String apiKey) {
    this.apiKey = apiKey;
  }

  /** {@code ticker(6자리)} 키로 DART 회사 정보 매핑. */
  public record CorpInfo(String corpCode, String corpName) {}

  /**
   * 결과 맵: {@code ticker (6자리) -> CorpInfo(corp_code, corp_name)}. 실패 시 빈 맵.
   */
  public Map<String, CorpInfo> fetchAll() {
    if (apiKey == null || apiKey.isBlank()) return Map.of();
    try {
      Connection.Response resp =
          Jsoup.connect(String.format(URL, apiKey))
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_0) AppleWebKit/537.36"
                      + " Chrome/120.0.0.0 Safari/537.36")
              .ignoreContentType(true)
              .maxBodySize(0)
              .timeout(30_000)
              .execute();
      byte[] zip = resp.bodyAsBytes();
      try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
          if (entry.getName().toLowerCase().endsWith(".xml")) {
            return parse(zis);
          }
        }
      }
      log.warn("DART corpCode zip contained no XML entry");
      return Map.of();
    } catch (Exception ex) {
      log.warn("DART corp_code fetch failed: {}", ex.getMessage());
      return Map.of();
    }
  }

  private Map<String, CorpInfo> parse(InputStream xml) throws Exception {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    XMLStreamReader reader = factory.createXMLStreamReader(xml);

    Map<String, CorpInfo> result = new HashMap<>();
    String corpCode = null;
    String corpName = null;
    String stockCode = null;
    StringBuilder text = new StringBuilder();

    while (reader.hasNext()) {
      int event = reader.next();
      switch (event) {
        case XMLStreamConstants.START_ELEMENT -> {
          text.setLength(0);
          if ("list".equals(reader.getLocalName())) {
            corpCode = null;
            corpName = null;
            stockCode = null;
          }
        }
        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> text.append(reader.getText());
        case XMLStreamConstants.END_ELEMENT -> {
          String name = reader.getLocalName();
          String value = text.toString().trim();
          switch (name) {
            case "corp_code" -> corpCode = value;
            case "corp_name" -> corpName = value;
            case "stock_code" -> stockCode = value;
            case "list" -> {
              if (corpCode != null
                  && !corpCode.isEmpty()
                  && stockCode != null
                  && !stockCode.isEmpty()
                  && !"NULL".equalsIgnoreCase(stockCode)) {
                String padded = stockCode;
                while (padded.length() < 6) padded = "0" + padded;
                result.put(padded, new CorpInfo(corpCode, corpName));
              }
            }
            default -> {}
          }
        }
        default -> {}
      }
    }
    return result;
  }
}
