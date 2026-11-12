package com.hongik.grad.radar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.dto.MetricContribution;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.radar.application.RadarService.UserView;
import com.hongik.grad.radar.dto.RadarPoint;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RadarServiceTest {

  private static final UUID USER = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-12T05:00:00Z");

  private final DisclosureRepository disclosureRepository = mock(DisclosureRepository.class);
  private final AnalysisReportRepository reportRepository = mock(AnalysisReportRepository.class);
  private final FavoriteRepository favoriteRepository = mock(FavoriteRepository.class);
  private final StockThemeRepository stockThemeRepository = mock(StockThemeRepository.class);
  private final ThemeRepository themeRepository = mock(ThemeRepository.class);
  private RadarService service;

  private final Theme semis = theme(1L, "반도체", 4);
  private final Theme bio = theme(2L, "바이오", 8);

  @BeforeEach
  void setUp() {
    service =
        new RadarService(
            disclosureRepository,
            reportRepository,
            favoriteRepository,
            stockThemeRepository,
            themeRepository,
            Clock.fixed(Instant.from(NOW), ZoneOffset.UTC));
    given(themeRepository.count()).willReturn(17L);
    given(reportRepository.findByDisclosureIdIn(anyCollection())).willReturn(List.of());
  }

  @Test
  void theta_usesStageIndexOverThemeCount() {
    assertThat(RadarService.theta(primary("005930", semis, 0.9), "005930", 17))
        .isEqualTo(4 * 360.0 / 17);
  }

  @Test
  void theta_withoutTheme_isStableHashAngleInRange() {
    double a = RadarService.theta(null, "123456", 17);
    assertThat(a).isEqualTo(RadarService.theta(null, "123456", 17)).isBetween(0.0, 359.999);
  }

  @Test
  void radius_ownFavorite_sameTheme_other() {
    UserView me = new UserView(Set.of("005930"), Set.of(1L));
    assertThat(RadarService.radius("005930", primary("005930", semis, 0.9), me)).isEqualTo(0.15);
    assertThat(RadarService.radius("000660", primary("000660", semis, 0.5), me))
        .isEqualTo(0.65 - 0.30 * 0.5);
    assertThat(RadarService.radius("068270", primary("068270", bio, 0.9), me)).isEqualTo(0.85);
    assertThat(RadarService.radius("999999", null, me)).isEqualTo(0.85);
  }

  @Test
  void topMetric_pickesLargestAbsoluteContribution() {
    assertThat(
            RadarService.topMetric(
                List.of(
                    new MetricContribution("roe", "ROE", 0.1, null, 1, 0.5),
                    new MetricContribution("opm", "영업이익률", 0.1, null, 1, -0.9))))
        .isEqualTo("영업이익률");
    assertThat(RadarService.topMetric(null)).isNull();
  }

  @Test
  void points_rejectsRangeOver24h_andBadFormat_andInvertedRange() {
    assertThatThrownBy(() -> service.points(USER, "2026-11-10T00:00:00Z", "2026-11-11T00:00:01Z"))
        .isInstanceOf(DomainException.class)
        .extracting(e -> ((DomainException) e).code())
        .isEqualTo(ErrorCode.INVALID_PARAM);
    assertThatThrownBy(() -> service.points(USER, "yesterday", null))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> service.points(USER, "2026-11-12T01:00:00Z", "2026-11-12T00:00:00Z"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void points_defaultsToLast30Minutes() {
    given(
            disclosureRepository.findByFiledAtGreaterThanEqualAndFiledAtLessThanOrderByFiledAtDesc(
                NOW.minusMinutes(30), NOW))
        .willReturn(List.of());
    assertThat(service.points(USER, null, null)).isEmpty();
  }

  @Test
  void points_mapsReportAndThemeAndIsMine() {
    Disclosure mine = disclosure(10L, "005930");
    Disclosure peer = disclosure(11L, "000660");
    given(favoriteRepository.findByFolderUserId(USER)).willReturn(List.of(favorite("005930")));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(any()))
        .willReturn(List.of(primary("005930", semis, 0.9), primary("000660", semis, 0.5)));
    given(
            disclosureRepository.findByFiledAtGreaterThanEqualAndFiledAtLessThanOrderByFiledAtDesc(
                any(), any()))
        .willReturn(List.of(mine, peer));
    AnalysisReport report = new AnalysisReport(mine.getStock(), semis, "r");
    report.setDisclosure(mine);
    report.setExpectedCar(new BigDecimal("-1.8400"));
    ReflectionTestUtils.setField(report, "generatedAt", NOW);
    given(reportRepository.findByDisclosureIdIn(anyCollection())).willReturn(List.of(report));

    List<RadarPoint> points = service.points(USER, null, null);

    assertThat(points).hasSize(2);
    RadarPoint p0 = points.get(0);
    assertThat(p0.isMine()).isTrue();
    assertThat(p0.radius()).isEqualTo(0.15);
    assertThat(p0.expectedCar()).isEqualTo(-1.84);
    assertThat(p0.theme()).isEqualTo("반도체");
    assertThat(p0.name()).isEqualTo("이름005930");
    assertThat(p0.type()).isEqualTo("event");
    RadarPoint p1 = points.get(1);
    assertThat(p1.isMine()).isFalse();
    assertThat(p1.radius()).isEqualTo(0.65 - 0.30 * 0.5);
    assertThat(p1.expectedCar()).isNull();
  }

  @Test
  void points_capKeepsMineEvenWhenOthersFillTheBudget() {
    List<Disclosure> rows = new ArrayList<>();
    IntStream.range(0, 600).forEach(i -> rows.add(disclosure((long) i, "OTHER" + i)));
    rows.add(disclosure(9999L, "005930")); // oldest row, would be cut by a naive limit
    given(favoriteRepository.findByFolderUserId(USER)).willReturn(List.of(favorite("005930")));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(any())).willReturn(List.of());
    given(
            disclosureRepository.findByFiledAtGreaterThanEqualAndFiledAtLessThanOrderByFiledAtDesc(
                any(), any()))
        .willReturn(rows);

    List<RadarPoint> points = service.points(USER, null, null);

    assertThat(points).hasSize(RadarService.MAX_POINTS);
    assertThat(points).anyMatch(p -> p.disclosureId() == 9999L && p.isMine());
    assertThat(points.get(0).disclosureId()).isZero(); // newest others survive
  }

  private static Theme theme(Long id, String name, int idx) {
    Theme t = new Theme(name);
    ReflectionTestUtils.setField(t, "id", id);
    t.setStage1ClassIdx(idx);
    return t;
  }

  private static StockTheme primary(String ticker, Theme theme, double probability) {
    StockTheme st =
        new StockTheme(new Stock(ticker, "이름" + ticker, Market.KOSPI), theme, (short) 1);
    st.setPrimary(true);
    st.setProbability(probability);
    return st;
  }

  private static Disclosure disclosure(Long id, String ticker) {
    Disclosure d =
        new Disclosure(
            new Stock(ticker, "이름" + ticker, Market.KOSPI),
            "F" + id,
            "제목" + id,
            DisclosureType.EVENT,
            NOW.minusMinutes(1));
    ReflectionTestUtils.setField(d, "id", id);
    return d;
  }

  private static Favorite favorite(String ticker) {
    return new Favorite(new Folder(USER, "기본", "#000000", FolderType.DEFAULT, 0), ticker);
  }
}
