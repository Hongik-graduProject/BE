package com.hongik.grad.disclosures.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hongik.grad.disclosures.client.dto.DartListItem;
import com.hongik.grad.stocks.domain.StockRepository;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Component
public class DartClient {

  private static final Logger log = LoggerFactory.getLogger(DartClient.class);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final String DEFAULT_BASE_URL = "https://opendart.fss.or.kr";

  private final WebClient webClient;
  private final String apiKey;
  private final StockRepository stockRepository;

  @Autowired
  public DartClient(
      WebClient.Builder webClientBuilder,
      @Value("${dart.api.key:}") String apiKey,
      @Value("${dart.api.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
      StockRepository stockRepository) {
    // opendart.fss.or.kr rejects the JDK TLSv1.3 ClientHello (protocol_version) and only offers
    // DHE-RSA ciphers on TLSv1.2. Netty's default cipher list is ECDHE-only, so we must pin
    // TLSv1.2 + the DHE-RSA suites the server actually negotiates (verified via a JDK probe).
    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(10))
            .secure(spec -> spec.sslContext(dartSslContext()));
    this.webClient =
        webClientBuilder
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    this.apiKey = apiKey;
    this.stockRepository = stockRepository;
  }

  private static SslContext dartSslContext() {
    try {
      return SslContextBuilder.forClient()
          .protocols("TLSv1.2")
          .ciphers(
              List.of(
                  "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256",
                  "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
                  "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
                  "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
                  "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                  "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"),
              SupportedCipherSuiteFilter.INSTANCE)
          .build();
    } catch (SSLException ex) {
      throw new IllegalStateException("Failed to build DART SSL context", ex);
    }
  }

  DartClient(WebClient webClient, String apiKey, StockRepository stockRepository) {
    this.webClient = webClient;
    this.apiKey = apiKey;
    this.stockRepository = stockRepository;
  }

  public List<DartListItem> fetchDisclosures(LocalDate from, int pageCount) {
    if (apiKey == null || apiKey.isBlank()) {
      log.debug("DART API key not configured; returning empty list");
      return List.of();
    }

    Set<String> universe = new HashSet<>(stockRepository.findAllTickers());

    try {
      List<DartListItem> allItems = new ArrayList<>();
      int pageNo = 1;

      while (true) {
        DartApiResponse response = fetchPage(from, pageNo, pageCount);

        if (response == null) {
          break;
        }

        if ("013".equals(response.status())) {
          log.debug("DART API returned no data for date {}", from);
          break;
        }

        if (!"000".equals(response.status())) {
          log.warn("DART API error: status={}, message={}", response.status(), response.message());
          break;
        }

        if (response.list() != null) {
          response.list().stream()
              .filter(item -> isInUniverse(item, universe))
              .forEach(allItems::add);
        }

        if (pageNo >= response.totalPage()) {
          break;
        }
        pageNo++;
      }

      return allItems;
    } catch (Exception ex) {
      log.warn("DART API call failed: {}", ex.getMessage());
      return List.of();
    }
  }

  private DartApiResponse fetchPage(LocalDate from, int pageNo, int pageCount) {
    return webClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/api/list.json")
                    .queryParam("crtfc_key", apiKey)
                    .queryParam("bgn_de", from.format(DATE_FMT))
                    .queryParam("page_no", pageNo)
                    .queryParam("page_count", pageCount)
                    .build())
        .retrieve()
        .bodyToMono(DartApiResponse.class)
        .retryWhen(
            Retry.backoff(2, Duration.ofSeconds(1))
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
        .block();
  }

  private boolean isInUniverse(DartListItem item, Set<String> universe) {
    if (item.stockCode() == null || item.stockCode().isBlank()) {
      return false;
    }
    return universe.contains(item.stockCode());
  }

  private boolean isRetryable(Throwable throwable) {
    if (throwable instanceof WebClientResponseException responseException) {
      return responseException.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS
          || responseException.getStatusCode().is5xxServerError();
    }
    return false;
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record DartApiResponse(
      String status,
      String message,
      int pageNo,
      int pageCount,
      int totalCount,
      int totalPage,
      List<DartListItem> list) {}
}
