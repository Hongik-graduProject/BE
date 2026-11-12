package com.hongik.grad.stocks.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 네트워크 없이 응답 파싱만 검증한다. KOSPI 지수 응답 형식은 실제로 확인하지 않았다. */
class NaverDailyPriceClientTest {

  private final NaverDailyPriceClient client = new NaverDailyPriceClient();

  @Test
  @DisplayName("종목 시세: 쉼표 숫자를 정수로, 날짜가 깨진 행은 건너뛴다")
  void parsesStockRows() throws Exception {
    String body =
        "[{\"localTradedAt\":\"2026-11-02\",\"closePrice\":\"230,000\","
            + "\"compareToPreviousClosePrice\":\"-5,000\",\"accumulatedTradingVolume\":10},"
            + "{\"localTradedAt\":\"bad\",\"closePrice\":\"1\"}]";

    var rows = client.parse(body, 0);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(rows.get(0).close()).isEqualTo(230_000L);
    assertThat(rows.get(0).change()).isEqualTo(-5_000L);
  }

  @Test
  @DisplayName("지수 시세: 소수 둘째 자리까지 0.01pt 단위 정수로 바꾼다")
  void parsesIndexWithScale() throws Exception {
    String body = "[{\"localTradedAt\":\"2026-11-02\",\"closePrice\":\"2,601.53\"}]";

    assertThat(client.parse(body, 2).get(0).close()).isEqualTo(260_153L);
  }

  @Test
  @DisplayName("배열이 아닌 응답은 빈 목록")
  void unexpectedShapeIsEmpty() throws Exception {
    assertThat(client.parse("{\"result\":{\"items\":[]}}", 2)).isEmpty();
  }
}
