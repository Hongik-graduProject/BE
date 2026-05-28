package com.hongik.grad.themes.application;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Normalizes ETL-side metric_code aliases (typically UPPERCASE, e.g. "PER", "EPS_GR", "DER") to the
 * canonical lowercase primary keys defined in {@code financial_metric_catalog} (V2 seed).
 *
 * <p>Kept inside {@code themes/} to preserve package-dependency direction — {@code analysis/} may
 * depend on {@code themes/} but not vice versa. Mirrors the alias set in {@code
 * analysis.application.MetricCanonicalCode} (single source of truth for the analysis layer).
 *
 * <p>Codes not present in this table are returned as their lowercase form; downstream FK insertion
 * may then fail loudly, which is the correct signal that the catalog needs extension.
 */
public final class MetricCodeNormalizer {

  private static final Map<String, String> ALIAS_TO_CATALOG_KEY =
      Map.ofEntries(
          Map.entry("per", "per"),
          Map.entry("pbr", "pbr"),
          Map.entry("eps", "eps"),
          Map.entry("roe", "roe"),
          Map.entry("roa", "roa"),
          Map.entry("op_margin", "op_margin"),
          Map.entry("net_margin", "net_margin"),
          Map.entry("sales_growth", "sales_growth"),
          Map.entry("op_growth", "op_growth"),
          Map.entry("debt_ratio", "debt_ratio"),
          Map.entry("der", "debt_ratio"),
          Map.entry("current_ratio", "current_ratio"),
          Map.entry("foreign_ratio", "foreign_ratio"),
          Map.entry("volatility", "volatility"),
          Map.entry("dividend_yield", "dividend_yield"),
          Map.entry("market_cap", "market_cap"),
          Map.entry("eps_gr", "eps"),
          Map.entry("eps_growth", "eps"),
          Map.entry("eps_growth_yoy", "eps"),
          Map.entry("epsgr", "eps"),
          Map.entry("eps_yoy", "eps"),
          Map.entry("rev_gr", "sales_growth"),
          Map.entry("revenue_growth", "sales_growth"),
          Map.entry("revgr", "sales_growth"),
          Map.entry("rev_yoy", "sales_growth"),
          Map.entry("revenue_growth_yoy", "sales_growth"));

  private MetricCodeNormalizer() {}

  /** Returns the catalog primary key for the given alias, or empty if no mapping exists. */
  public static Optional<String> toCatalogKey(String alias) {
    if (alias == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(ALIAS_TO_CATALOG_KEY.get(alias.toLowerCase(Locale.ROOT)));
  }
}
