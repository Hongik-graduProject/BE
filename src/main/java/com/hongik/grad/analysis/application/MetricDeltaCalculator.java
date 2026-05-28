package com.hongik.grad.analysis.application;

import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    List<FinancialStatement> statements =
        financialStatementRepository.findByStockTickerOrderByPeriodDesc(ticker);
    if (statements.isEmpty()) {
      return DeltaSnapshot.empty();
    }
    FinancialStatement latest = statements.get(0);
    FinancialStatement previous = statements.size() > 1 ? statements.get(1) : null;
    return new DeltaSnapshot(latest, previous);
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

    return MetricCanonicalCode.computeDelta(canonical, latestValue.get(), previousValue);
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

  /** Pair of latest + previous {@link FinancialStatement} (either may be null when missing). */
  public record DeltaSnapshot(FinancialStatement latest, FinancialStatement previous) {
    public static DeltaSnapshot empty() {
      return new DeltaSnapshot(null, null);
    }
  }
}
