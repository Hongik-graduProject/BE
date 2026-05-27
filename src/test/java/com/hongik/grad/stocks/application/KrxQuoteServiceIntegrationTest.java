package com.hongik.grad.stocks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hongik.grad.stocks.client.KrxClient;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class KrxQuoteServiceIntegrationTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add("spring.autoconfigure.exclude", () -> "");
  }

  @MockBean private KrxClient krxClient;

  @Autowired private KrxQuoteService krxQuoteService;

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearRedis() {
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
  }

  @Test
  @DisplayName("첫 번째 호출은 KrxClient를 호출하고, 두 번째 호출은 캐시에서 반환한다")
  void getQuote_cacheHitOnSecondCall() {
    KrxQuote expected = new KrxQuote("005930", 72000, 1.5, 1000);
    given(krxClient.getQuote("005930")).willReturn(expected);

    KrxQuote first = krxQuoteService.getQuote("005930");
    KrxQuote second = krxQuoteService.getQuote("005930");

    assertThat(first).isEqualTo(expected);
    assertThat(second).isEqualTo(expected);
    verify(krxClient, times(1)).getQuote("005930");
  }

  @Test
  @DisplayName("getQuotes 배치 호출 시 캐시된 종목은 KrxClient를 호출하지 않는다")
  void getQuotes_batchCaching() {
    KrxQuote samsung = new KrxQuote("005930", 72000, 1.5, 1000);
    KrxQuote sk = new KrxQuote("000660", 130000, -0.5, -500);
    given(krxClient.getQuote("005930")).willReturn(samsung);
    given(krxClient.getQuote("000660")).willReturn(sk);

    krxQuoteService.getQuote("005930");

    List<KrxQuote> quotes = krxQuoteService.getQuotes(List.of("005930", "000660"));

    assertThat(quotes).hasSize(2);
    assertThat(quotes.get(0)).isEqualTo(samsung);
    assertThat(quotes.get(1)).isEqualTo(sk);
    verify(krxClient, times(1)).getQuote("005930");
    verify(krxClient, times(1)).getQuote("000660");
  }
}
