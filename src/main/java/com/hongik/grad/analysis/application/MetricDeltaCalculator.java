package com.hongik.grad.analysis.application;

import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.PeriodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Computes the delta of a financial metric between the latest two periods using {@link
 * MetricCanonicalCode} alias resolution. Supports RAW_DIFF (ratio metrics) and YOY_PERCENT
 * (growth-rate metrics such as EPS_GR, REV_GR).
 *
 * <p>v1.0 limitation: no z-score normalization (documented in AnalysisReportGenerator).
 */
@Component
public class MetricDeltaCalculator {

  private static final Logger log = LoggerFactory.getLogger(MetricDeltaCalculator.class);
  private static final int SCALE = 6;

  private final FinancialStatementRepository financialStatementRepository;

  public MetricDeltaCalculator(FinancialStatementRepository financialStatementRepository) {
    this.financialStatementRepository = financialStatementRepository;
  }

  public DeltaSnapshot computeForTicker(String ticker) {
    // 같은 기간에 BS·CF 행도 따로 있으므로 손익계산서(CIS) 행만 쓴다.
    // 연간 행이 두 개 이상이면 연간 대 연간으로 비교한다.
    List<FinancialStatement> annual =
        incomeStatements(
            financialStatementRepository.findByStockTickerAndPeriodTypeOrderByPeriodDesc(
                ticker, PeriodType.ANNUAL));
    if (annual.size() >= 2) {
      return new DeltaSnapshot(annual.get(0), annual.get(1));
    }
    // DART 분기 행(Q1·H1·Q3)은 누적치라 서로 비교할 수 없고 문자열 정렬도 시간 순이 아니다 ("2026Q1" > "2026H1").
    // 시간 순 최신 행을 전년 같은 기간과 비교한다 (2026H1 ↔ 2025H1). 없으면 previous 는 null.
    List<FinancialStatement> all =
        incomeStatements(financialStatementRepository.findByStockTickerOrderByPeriodDesc(ticker));
    if (all.isEmpty()) {
      return DeltaSnapshot.empty();
    }
    FinancialStatement latest =
        all.stream().max(Comparator.comparing(f -> periodKey(f.getPeriod()))).orElseThrow();
    String prevPeriod = sameQuarterLastYear(latest.getPeriod());
    FinancialStatement previous =
        all.stream().filter(f -> f.getPeriod().equals(prevPeriod)).findFirst().orElse(null);
    return new DeltaSnapshot(latest, previous);
  }

  /** "2025Q1" < "2025H1" < "2025Q3" < "2025Q4" 순서가 되게 하는 정렬 키. */
  static String periodKey(String period) {
    if (period == null || period.length() < 6) return String.valueOf(period);
    String order =
        switch (period.substring(4)) {
          case "Q1" -> "1";
          case "H1" -> "2";
          case "Q3" -> "3";
          case "Q4" -> "4";
          default -> period.substring(4);
        };
    return period.substring(0, 4) + order;
  }

  static String sameQuarterLastYear(String period) {
    try {
      return (Integer.parseInt(period.substring(0, 4)) - 1) + period.substring(4);
    } catch (RuntimeException e) {
      return "";
    }
  }

  /**
   * Resolves {@code metricCode} via {@link MetricCanonicalCode} alias lookup (case-insensitive),
   * then computes the delta using the canonical strategy. Returns {@code Optional.empty()} when the
   * alias is unrecognized or raw value extraction fails; callers should treat that as
   * contribution=0.
   */
  public Optional<Double> delta(DeltaSnapshot snapshot, String metricCode) {
    if (snapshot.latest() == null) {
      return Optional.empty();
    }

    Optional<MetricCanonicalCode> canonicalOpt =
        MetricCanonicalCode.fromAlias(metricCode.toLowerCase(Locale.ROOT));
    if (canonicalOpt.isEmpty()) {
      log.debug("Unrecognized metric_code alias — contribution=0: {}", metricCode);
      return Optional.empty();
    }
    MetricCanonicalCode canonical = canonicalOpt.get();

    Optional<BigDecimal> latestValue = extractRaw(snapshot.latest(), canonical);
    if (latestValue.isEmpty()) {
      return Optional.empty();
    }

    BigDecimal previousValue =
        snapshot.previous() == null
            ? null
            : extractRaw(snapshot.previous(), canonical).orElse(null);

    return MetricCanonicalCode.computeDelta(canonical, latestValue.get(), previousValue)
        .map(d -> clamp(d, canonical.strategy()));
  }

  /**
   * 정규화(z-score)가 없어서 극단값이 그대로 β 에 곱해진다 (예: 지주회사 지분법 이익으로 영업이익률 600%). 비율 차이는 ±20%p, 성장률은 ±50% 로
   * 자른다.
   */
  static double clamp(double delta, MetricCanonicalCode.ComputeStrategy strategy) {
    double cap = strategy == MetricCanonicalCode.ComputeStrategy.YOY_PERCENT ? 50.0 : 20.0;
    return Math.max(-cap, Math.min(cap, delta));
  }

  /**
   * Extracts the base BigDecimal value for a canonical metric from a single FinancialStatement.
   * Growth-rate metrics extract the raw quantity; the YOY_PERCENT formula is applied later in
   * {@link MetricCanonicalCode#computeDelta}.
   */
  private Optional<BigDecimal> extractRaw(FinancialStatement fs, MetricCanonicalCode canonical) {
    return switch (canonical) {
      case PER -> Optional.ofNullable(fs.getPer());
      case PBR -> Optional.ofNullable(fs.getPbr());
      case EPS -> Optional.ofNullable(fs.getEps()).map(BigDecimal::valueOf);
      case ROE -> ratio(fs.getNetIncome(), fs.getTotalEquity());
      case ROA -> ratio(fs.getNetIncome(), fs.getTotalAssets());
      case OP_MARGIN -> ratio(fs.getOperatingProfit(), fs.getRevenue());
      case NET_MARGIN -> ratio(fs.getNetIncome(), fs.getRevenue());
      case DEBT_RATIO -> ratio(fs.getTotalLiabilities(), fs.getTotalEquity());
      case CURRENT_RATIO -> Optional.empty();
      case FOREIGN_RATIO -> Optional.empty();
      case VOLATILITY -> Optional.empty();
      case DIVIDEND_YIELD -> Optional.ofNullable(fs.getDividendYield());
      case MARKET_CAP -> Optional.empty();
      case EPS_GROWTH_YOY -> Optional.ofNullable(fs.getEps()).map(BigDecimal::valueOf);
      case REVENUE_GROWTH_YOY -> Optional.ofNullable(fs.getRevenue()).map(BigDecimal::valueOf);
    };
  }

  private Optional<BigDecimal> ratio(Long numerator, Long denominator) {
    if (numerator == null || denominator == null || denominator == 0L) {
      return Optional.empty();
    }
    return Optional.of(
        BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)));
  }

  private static List<FinancialStatement> incomeStatements(List<FinancialStatement> rows) {
    return rows.stream().filter(f -> "CIS".equals(f.getSjDiv())).toList();
  }

  /** Pair of latest + previous {@link FinancialStatement} (either may be null when missing). */
  public record DeltaSnapshot(FinancialStatement latest, FinancialStatement previous) {
    public static DeltaSnapshot empty() {
      return new DeltaSnapshot(null, null);
    }
  }
}
