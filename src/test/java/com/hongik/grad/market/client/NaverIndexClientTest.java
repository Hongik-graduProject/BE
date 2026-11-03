package com.hongik.grad.market.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.market.dto.MarketIndexResponse;
import org.junit.jupiter.api.Test;

class NaverIndexClientTest {

  private final ObjectMapper om = new ObjectMapper();

  @Test
  void parse_상승() throws Exception {
    String json =
        """
        {"datas":[{"closePrice":"5,859.08","compareToPreviousClosePrice":"128.34",
          "compareToPreviousPrice":{"code":"2","name":"RISING"},"fluctuationsRatio":"2.24"}]}
        """;
    assertThat(NaverIndexClient.parse("KOSPI", "코스피", om.readTree(json)))
        .contains(new MarketIndexResponse("KOSPI", "코스피", 5859.08, 128.34, 2.24));
  }

  @Test
  void parse_하락이면_음수() throws Exception {
    String json =
        """
        {"datas":[{"closePrice":"1,000.12","compareToPreviousClosePrice":"3.10",
          "compareToPreviousPrice":{"code":"5","name":"FALLING"},"fluctuationsRatio":"0.31"}]}
        """;
    assertThat(NaverIndexClient.parse("KOSDAQ", "코스닥", om.readTree(json)))
        .contains(new MarketIndexResponse("KOSDAQ", "코스닥", 1000.12, -3.1, -0.31));
  }

  @Test
  void parse_데이터가_없거나_값이_깨지면_빈값() throws Exception {
    assertThat(NaverIndexClient.parse("KOSPI", "코스피", om.readTree("{\"datas\":[]}"))).isEmpty();
    assertThat(NaverIndexClient.parse("KOSPI", "코스피", om.readTree("{}"))).isEmpty();
    assertThat(
            NaverIndexClient.parse(
                "KOSPI", "코스피", om.readTree("{\"datas\":[{\"closePrice\":\"-\"}]}")))
        .isEmpty();
  }
}
