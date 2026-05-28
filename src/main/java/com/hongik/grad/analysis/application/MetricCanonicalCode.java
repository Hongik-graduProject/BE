package com.hongik.grad.analysis.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical mapping from ETL-loaded metric_code aliases (e.g. UPPERCASE from ThemeSense ETL) to the
 * V2 seed catalog's lowercase primary keys and the correct delta-computation strategy.
 *
 * <p>Strategy RAW_DIFF: delta = latest - previous (suitable for ratio metrics like PER, ROE). <br>
 * Strategy YOY_PERCENT: delta = (latest - previous) / |previous| * 100 (growth-rate metrics).
 */
public enum MetricCanonicalCode {
  PER("per", ComputeStrategy.RAW_DIFF, "per", "pbr"),
  PBR("pbr", ComputeStrategy.RAW_DIFF, "pbr"),
  EPS("eps", ComputeStrategy.RAW_DIFF, "eps"),
  ROE("roe", ComputeStrategy.RAW_DIFF, "roe"),
  ROA("roa", ComputeStrategy.RAW_DIFF, "roa"),
  OP_MARGIN("op_margin", ComputeStrategy.RAW_DIFF, "op_margin"),
  NET_MARGIN("net_margin", ComputeStrategy.RAW_DIFF, "net_margin"),
  DEBT_RATIO("debt_ratio", ComputeStrategy.RAW_DIFF, "debt_ratio", "der"),
  CURRENT_RATIO("current_ratio", ComputeStrategy.RAW_DIFF, "current_ratio"),
  FOREIGN_RATIO("foreign_ratio", ComputeStrategy.RAW_DIFF, "foreign_ratio"),
  VOLATILITY("volatility", ComputeStrategy.RAW_DIFF, "volatility"),
  DIVIDEND_YIELD("dividend_yield", ComputeStrategy.RAW_DIFF, "dividend_yield"),
  MARKET_CAP("market_cap", ComputeStrategy.RAW_DIFF, "market_cap"),
  EPS_GROWTH_YOY(
      "eps",
      ComputeStrategy.YOY_PERCENT,
      "eps_gr",
      "eps_growth",
      "eps_growth_yoy",
      "epsgr",
      "eps_yoy"),
  REVENUE_GROWTH_YOY(
      "sales_growth",
      ComputeStrategy.YOY_PERCENT,
      "rev_gr",
      "revenue_growth",
      "sales_growth",
      "revgr",
      "rev_yoy",
      "revenue_growth_yoy");

  public enum ComputeStrategy {
    RAW_DIFF,
    YOY_PERCENT
  }

  private final String catalogKey;
  private final ComputeStrategy strategy;
  private final Set<String> aliases;

  private static final Map<String, MetricCanonicalCode> ALIAS_MAP;

  static {
    ALIAS_MAP = new HashMap<>();
    for (MetricCanonicalCode code : values()) {
      for (String alias : code.aliases) {
        ALIAS_MAP.put(alias.toLowerCase(Locale.ROOT), code);
      }
    }
  }

  MetricCanonicalCode(String catalogKey, ComputeStrategy strategy, String... aliases) {
    this.catalogKey = catalogKey;
    this.strategy = strategy;
    this.aliases = Set.of(aliases);
  }

  /** Returns the lowercase PK used in the V2 financial_metric_catalog seed. */
  public String catalogKey() {
    return catalogKey;
  }

  public ComputeStrategy strategy() {
    return strategy;
  }

  /** Resolves an alias (case-insensitive) to a canonical code. Returns empty if not recognized. */
  public static Optional<MetricCanonicalCode> fromAlias(String alias) {
    if (alias == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(ALIAS_MAP.get(alias.toLowerCase(Locale.ROOT)));
  }

  /**
   * Computes the delta value from latest/previous BigDecimal raw values using this entry's
   * strategy. Returns empty when inputs are insufficient.
   */
  public static Optional<Double> computeDelta(
      MetricCanonicalCode canonical, BigDecimal latest, BigDecimal previous) {
    if (latest == null) {
      return Optional.empty();
    }
    if (previous == null) {
      return Optional.of(0.0);
    }
    return switch (canonical.strategy) {
      case RAW_DIFF ->
          Optional.of(latest.subtract(previous).setScale(6, RoundingMode.HALF_UP).doubleValue());
      case YOY_PERCENT -> {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
          yield Optional.of(0.0);
        }
        BigDecimal diff = latest.subtract(previous);
        BigDecimal absPrev = previous.abs();
        yield Optional.of(
            diff.divide(absPrev, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue());
      }
    };
  }
}
