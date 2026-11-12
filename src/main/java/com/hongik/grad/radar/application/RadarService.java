package com.hongik.grad.radar.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.radar.dto.RadarPoint;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.ThemeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds radar points: theta from the primary theme, radius from the caller's favorites. */
@Service
public class RadarService {

  static final int MAX_POINTS = 500;
  static final Duration DEFAULT_WINDOW = Duration.ofMinutes(30);
  static final Duration MAX_RANGE = Duration.ofHours(24);
  static final double RADIUS_MINE = 0.15;
  static final double RADIUS_OTHER = 0.85;

  private final DisclosureRepository disclosureRepository;
  private final AnalysisReportRepository analysisReportRepository;
  private final FavoriteRepository favoriteRepository;
  private final StockThemeRepository stockThemeRepository;
  private final ThemeRepository themeRepository;
  private final Clock clock;

  public RadarService(
      DisclosureRepository disclosureRepository,
      AnalysisReportRepository analysisReportRepository,
      FavoriteRepository favoriteRepository,
      StockThemeRepository stockThemeRepository,
      ThemeRepository themeRepository,
      Clock clock) {
    this.disclosureRepository = disclosureRepository;
    this.analysisReportRepository = analysisReportRepository;
    this.favoriteRepository = favoriteRepository;
    this.stockThemeRepository = stockThemeRepository;
    this.themeRepository = themeRepository;
    this.clock = clock;
  }

  /**
   * Points filed in [from, to), newest first. Own-favorite points are always kept; the 500 cap
   * trims the others.
   */
  @Transactional(readOnly = true)
  public List<RadarPoint> points(UUID userId, String fromParam, String toParam) {
    OffsetDateTime to = toParam == null ? OffsetDateTime.now(clock) : parse("to", toParam);
    OffsetDateTime from = fromParam == null ? to.minus(DEFAULT_WINDOW) : parse("from", fromParam);
    if (!from.isBefore(to)) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "from must be before to");
    }
    if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "range must be at most 24 hours");
    }

    List<Disclosure> disclosures =
        disclosureRepository.findByFiledAtGreaterThanEqualAndFiledAtLessThanOrderByFiledAtDesc(
            from, to);
    UserView me = userView(userId);
    List<Disclosure> kept = new ArrayList<>();
    int others = 0;
    int otherBudget =
        MAX_POINTS
            - (int)
                disclosures.stream()
                    .filter(d -> me.tickers().contains(d.getStock().getTicker()))
                    .count();
    for (Disclosure d : disclosures) {
      if (me.tickers().contains(d.getStock().getTicker())) {
        kept.add(d);
      } else if (others < otherBudget) {
        kept.add(d);
        others++;
      }
    }
    return toPoints(kept, me, null);
  }

  /** Per-user points for one disclosure, pushed over SSE right after it is committed. */
  @Transactional(readOnly = true)
  public Map<UUID, RadarPoint> streamPoints(
      Long disclosureId, String event, Collection<UUID> userIds) {
    return disclosureRepository
        .findById(disclosureId)
        .map(d -> pointsPerUser(d, event, userIds))
        .orElse(Map.of());
  }

  /** Same as {@link #streamPoints} but resolves the disclosure behind a freshly created report. */
  @Transactional(readOnly = true)
  public Map<UUID, RadarPoint> streamPointsForReport(Long reportId, Collection<UUID> userIds) {
    return analysisReportRepository
        .findById(reportId)
        .map(AnalysisReport::getDisclosure)
        .map(d -> pointsPerUser(d, "report", userIds))
        .orElse(Map.of());
  }

  private Map<UUID, RadarPoint> pointsPerUser(
      Disclosure disclosure, String event, Collection<UUID> userIds) {
    Map<UUID, RadarPoint> result = new HashMap<>();
    for (UUID userId : userIds) {
      result.put(userId, toPoints(List.of(disclosure), userView(userId), event).get(0));
    }
    return result;
  }

  private List<RadarPoint> toPoints(List<Disclosure> disclosures, UserView me, String event) {
    if (disclosures.isEmpty()) {
      return List.of();
    }
    Set<String> tickers =
        disclosures.stream().map(d -> d.getStock().getTicker()).collect(Collectors.toSet());
    Map<String, StockTheme> primaryByTicker = primaryThemes(tickers);
    Map<Long, AnalysisReport> reportByDisclosure =
        analysisReportRepository
            .findByDisclosureIdIn(disclosures.stream().map(Disclosure::getId).toList())
            .stream()
            .collect(
                Collectors.toMap(
                    r -> r.getDisclosure().getId(),
                    Function.identity(),
                    // several runs per disclosure: show the latest
                    (a, b) -> a.getGeneratedAt().isAfter(b.getGeneratedAt()) ? a : b));
    long themeCount = themeRepository.count();

    List<RadarPoint> points = new ArrayList<>(disclosures.size());
    for (Disclosure d : disclosures) {
      String ticker = d.getStock().getTicker();
      StockTheme primary = primaryByTicker.get(ticker);
      AnalysisReport report = reportByDisclosure.get(d.getId());
      points.add(
          new RadarPoint(
              d.getId(),
              ticker,
              d.getStock().getName(),
              d.getTitle(),
              d.getType().getValue(),
              primary == null ? null : primary.getTheme().getName(),
              theta(primary, ticker, themeCount),
              radius(ticker, primary, me),
              report == null || report.getExpectedCar() == null
                  ? null
                  : report.getExpectedCar().doubleValue(),
              report == null ? null : topMetric(report.getMetricContributions()),
              d.getFiledAt(),
              me.tickers().contains(ticker),
              event));
    }
    return points;
  }

  private UserView userView(UUID userId) {
    Set<String> myTickers =
        favoriteRepository.findByFolderUserId(userId).stream()
            .map(Favorite::getTicker)
            .collect(Collectors.toSet());
    Set<Long> myThemeIds = new HashSet<>();
    primaryThemes(myTickers).values().forEach(st -> myThemeIds.add(st.getTheme().getId()));
    return new UserView(myTickers, myThemeIds);
  }

  private Map<String, StockTheme> primaryThemes(Set<String> tickers) {
    if (tickers.isEmpty()) {
      return Map.of();
    }
    return stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(tickers).stream()
        .collect(
            Collectors.toMap(
                st -> st.getStock().getTicker(),
                Function.identity(),
                // several model versions may each mark a primary: keep the more probable one
                (a, b) -> a.getProbability() >= b.getProbability() ? a : b));
  }

  static double theta(StockTheme primary, String ticker, long themeCount) {
    if (primary != null && primary.getTheme().getStage1ClassIdx() != null && themeCount > 0) {
      return (primary.getTheme().getStage1ClassIdx() * 360.0 / themeCount) % 360.0;
    }
    // no theme: a stable pseudo-random angle so the same stock always lands in the same place
    return Math.floorMod(ticker.hashCode(), 360_000) / 1000.0;
  }

  static double radius(String ticker, StockTheme primary, UserView me) {
    if (me.tickers().contains(ticker)) {
      return RADIUS_MINE;
    }
    if (primary != null && me.themeIds().contains(primary.getTheme().getId())) {
      return 0.65 - 0.30 * primary.getProbability();
    }
    return RADIUS_OTHER;
  }

  static String topMetric(List<MetricContribution> contributions) {
    if (contributions == null || contributions.isEmpty()) {
      return null;
    }
    return contributions.stream()
        .max(Comparator.comparingDouble(c -> Math.abs(c.contribution())))
        .map(MetricContribution::displayName)
        .orElse(null);
  }

  private static OffsetDateTime parse(String name, String value) {
    try {
      return OffsetDateTime.parse(value);
    } catch (DateTimeParseException ex) {
      throw new DomainException(
          ErrorCode.INVALID_PARAM, name + " must be an ISO-8601 date-time with offset");
    }
  }

  record UserView(Set<String> tickers, Set<Long> themeIds) {}
}
