package com.hongik.grad.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricCanonicalCodeTest {

  @Test
  @DisplayName("UPPERCASE ETL aliases resolve to correct canonical codes")
  void fromAlias_uppercaseEtlAliases() {
    assertThat(MetricCanonicalCode.fromAlias("PER")).contains(MetricCanonicalCode.PER);
    assertThat(MetricCanonicalCode.fromAlias("ROE")).contains(MetricCanonicalCode.ROE);
    assertThat(MetricCanonicalCode.fromAlias("DER")).contains(MetricCanonicalCode.DEBT_RATIO);
    assertThat(MetricCanonicalCode.fromAlias("EPS_GR"))
        .contains(MetricCanonicalCode.EPS_GROWTH_YOY);
    assertThat(MetricCanonicalCode.fromAlias("REV_GR"))
        .contains(MetricCanonicalCode.REVENUE_GROWTH_YOY);
    assertThat(MetricCanonicalCode.fromAlias("OP_MARGIN")).contains(MetricCanonicalCode.OP_MARGIN);
  }

  @Test
  @DisplayName("Lowercase aliases also resolve correctly")
  void fromAlias_lowercase() {
    assertThat(MetricCanonicalCode.fromAlias("per")).contains(MetricCanonicalCode.PER);
    assertThat(MetricCanonicalCode.fromAlias("roe")).contains(MetricCanonicalCode.ROE);
    assertThat(MetricCanonicalCode.fromAlias("debt_ratio"))
        .contains(MetricCanonicalCode.DEBT_RATIO);
    assertThat(MetricCanonicalCode.fromAlias("eps_gr"))
        .contains(MetricCanonicalCode.EPS_GROWTH_YOY);
    assertThat(MetricCanonicalCode.fromAlias("sales_growth"))
        .contains(MetricCanonicalCode.REVENUE_GROWTH_YOY);
  }

  @Test
  @DisplayName("Unknown alias returns empty")
  void fromAlias_unknown_returnsEmpty() {
    assertThat(MetricCanonicalCode.fromAlias("UNKNOWN_XYZ")).isEmpty();
    assertThat(MetricCanonicalCode.fromAlias(null)).isEmpty();
    assertThat(MetricCanonicalCode.fromAlias("")).isEmpty();
  }

  @Test
  @DisplayName("catalogKey matches V2 seed lowercase PK for ratio metrics")
  void catalogKey_matchesSeedPk() {
    assertThat(MetricCanonicalCode.PER.catalogKey()).isEqualTo("per");
    assertThat(MetricCanonicalCode.ROE.catalogKey()).isEqualTo("roe");
    assertThat(MetricCanonicalCode.DEBT_RATIO.catalogKey()).isEqualTo("debt_ratio");
    assertThat(MetricCanonicalCode.EPS_GROWTH_YOY.catalogKey()).isEqualTo("eps");
    assertThat(MetricCanonicalCode.REVENUE_GROWTH_YOY.catalogKey()).isEqualTo("sales_growth");
  }

  @Test
  @DisplayName("RAW_DIFF strategy: delta = latest - previous")
  void computeDelta_rawDiff() {
    BigDecimal latest = new BigDecimal("15.00");
    BigDecimal previous = new BigDecimal("12.00");
    Optional<Double> result =
        MetricCanonicalCode.computeDelta(MetricCanonicalCode.PER, latest, previous);
    assertThat(result).contains(3.0);
  }

  @Test
  @DisplayName("YOY_PERCENT strategy: delta = (latest-prev)/|prev| * 100")
  void computeDelta_yoyPercent_positive() {
    BigDecimal latest = new BigDecimal("1100");
    BigDecimal previous = new BigDecimal("1000");
    Optional<Double> result =
        MetricCanonicalCode.computeDelta(MetricCanonicalCode.EPS_GROWTH_YOY, latest, previous);
    // (1100-1000)/1000 * 100 = 10.0
    assertThat(result).isPresent();
    assertThat(result.get()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.001));
  }

  @Test
  @DisplayName("YOY_PERCENT with zero previous returns 0.0")
  void computeDelta_yoyPercent_zeroPrevious() {
    BigDecimal latest = new BigDecimal("500");
    BigDecimal previous = BigDecimal.ZERO;
    Optional<Double> result =
        MetricCanonicalCode.computeDelta(MetricCanonicalCode.REVENUE_GROWTH_YOY, latest, previous);
    assertThat(result).contains(0.0);
  }

  @Test
  @DisplayName("Null latest returns empty")
  void computeDelta_nullLatest_returnsEmpty() {
    Optional<Double> result =
        MetricCanonicalCode.computeDelta(MetricCanonicalCode.PER, null, new BigDecimal("10"));
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("Null previous returns 0.0")
  void computeDelta_nullPrevious_returnsZero() {
    Optional<Double> result =
        MetricCanonicalCode.computeDelta(MetricCanonicalCode.PER, new BigDecimal("12"), null);
    assertThat(result).contains(0.0);
  }
}
