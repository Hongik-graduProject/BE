package com.hongik.grad.stocks.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.stocks.client.NaverFinanceMetricsClient.Metrics;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NaverFinanceMetricsClientTest {

  @Test
  void parseKrw_조와억을_원으로_바꾼다() {
    assertThat(NaverFinanceMetricsClient.parseKrw("1,344조 6,441억"))
        .isEqualTo(1_344_644_100_000_000L);
    assertThat(NaverFinanceMetricsClient.parseKrw("5조")).isEqualTo(5_000_000_000_000L);
    assertThat(NaverFinanceMetricsClient.parseKrw("6,441억")).isEqualTo(644_100_000_000L);
    assertThat(NaverFinanceMetricsClient.parseKrw("N/A")).isNull();
    assertThat(NaverFinanceMetricsClient.parseKrw(null)).isNull();
  }

  @Test
  void parse_totalInfos_전체() throws Exception {
    String json =
        """
        [{"code":"marketValue","value":"1,344조 6,441억"},
         {"code":"highPriceOf52Weeks","value":"380,000"},
         {"code":"lowPriceOf52Weeks","value":"93,500"},
         {"code":"per","value":"10.32배"},
         {"code":"eps","value":"22,292원"},
         {"code":"pbr","value":"2.67배"},
         {"code":"dividendYieldRatio","value":"0.73%"}]
        """;
    Metrics m = NaverFinanceMetricsClient.parse(new ObjectMapper().readTree(json));

    assertThat(m.marketCap()).isEqualTo(1_344_644_100_000_000L);
    assertThat(m.high52w()).isEqualTo(380_000L);
    assertThat(m.low52w()).isEqualTo(93_500L);
    assertThat(m.per()).isEqualByComparingTo(new BigDecimal("10.32"));
    assertThat(m.pbr()).isEqualByComparingTo(new BigDecimal("2.67"));
    assertThat(m.eps()).isEqualTo(22_292L);
    assertThat(m.dividendYield()).isEqualByComparingTo(new BigDecimal("0.73"));
  }

  @Test
  void parse_항목이_없으면_null() throws Exception {
    Metrics m = NaverFinanceMetricsClient.parse(new ObjectMapper().readTree("[]"));
    assertThat(m).isEqualTo(Metrics.empty());
  }
}
