package com.hongik.grad.folders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.folders.dto.FolderItemsResponse;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.stocks.dto.KrxQuote;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import com.hongik.grad.themes.domain.Theme;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

  @Mock private FavoriteRepository favoriteRepository;
  @Mock private FolderRepository folderRepository;
  @Mock private KrxQuoteService krxQuoteService;
  @Mock private StockRepository stockRepository;
  @Mock private StockThemeRepository stockThemeRepository;
  @Mock private AnalysisReportRepository analysisReportRepository;
  @Mock private ReportReadStatusRepository reportReadStatusRepository;

  @InjectMocks private FavoriteService favoriteService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final Long FOLDER_ID = 1L;

  private static Stock stock(String ticker, String name) {
    return new Stock(ticker, name, Market.KOSPI);
  }

  private static Theme theme(long id, String name) throws Exception {
    Theme t = new Theme(name);
    setField(t, "id", id);
    return t;
  }

  private static AnalysisReport report(
      long id, Stock stock, Theme appliedTheme, BigDecimal expectedCar, String summary)
      throws Exception {
    AnalysisReport r = new AnalysisReport(stock, appliedTheme, "title");
    setField(r, "id", id);
    r.setExpectedCar(expectedCar);
    r.setSummary(summary);
    return r;
  }

  private static StockTheme primaryStockTheme(Stock stock, Theme theme) {
    StockTheme st = new StockTheme(stock, theme, (short) 1);
    st.setPrimary(true);
    return st;
  }

  private static void setField(Object target, String field, Object value) throws Exception {
    Field f = findField(target.getClass(), field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
    Class<?> c = type;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  @Test
  @DisplayName("list()는 folder 헤더와 시세/종목명을 포함한 항목을 반환한다")
  void list_returnsHeaderAndItemsWithPrices() {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));

    Favorite fav = new Favorite(folder, "005930");
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of(fav));

    given(krxQuoteService.getQuotes(List.of("005930")))
        .willReturn(List.of(new KrxQuote("005930", 72000, 1.5, 1000)));
    given(stockRepository.findAllById(List.of("005930")))
        .willReturn(List.of(stock("005930", "삼성전자")));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(List.of("005930")))
        .willReturn(List.of());
    given(analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(List.of("005930")))
        .willReturn(List.of());

    FolderItemsResponse result = favoriteService.list(USER_ID, FOLDER_ID);

    assertThat(result.folder().id()).isEqualTo(folder.getId());
    assertThat(result.folder().type()).isEqualTo(FolderType.CUSTOM);
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).ticker()).isEqualTo("005930");
    assertThat(result.items().get(0).name()).isEqualTo("삼성전자");
    assertThat(result.items().get(0).currentPrice()).isEqualTo(72000L);
  }

  @Test
  @DisplayName("list()는 즐겨찾기가 비어있으면 빈 items를 반환한다")
  void list_emptyFavorites_returnsEmptyItems() {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of());

    FolderItemsResponse result = favoriteService.list(USER_ID, FOLDER_ID);

    assertThat(result.items()).isEmpty();
  }

  @Test
  @DisplayName("list()는 최신 report가 있으면 has_theme=true와 report 필드를 채운다")
  void list_withLatestReport_hasThemeTrue() throws Exception {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));

    Favorite fav = new Favorite(folder, "005930");
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of(fav));

    Stock samsung = stock("005930", "삼성전자");
    given(krxQuoteService.getQuotes(List.of("005930")))
        .willReturn(List.of(new KrxQuote("005930", 72000, 1.5, 1000)));
    given(stockRepository.findAllById(List.of("005930"))).willReturn(List.of(samsung));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(List.of("005930")))
        .willReturn(List.of());

    Theme hbm = theme(1L, "HBM/메모리");
    AnalysisReport rpt = report(501L, samsung, hbm, new BigDecimal("6.8"), "HBM 수주 호조");
    given(analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(List.of("005930")))
        .willReturn(List.of(rpt));
    given(reportReadStatusRepository.findByUserIdAndReportIdIn(USER_ID, List.of(501L)))
        .willReturn(List.of());

    FolderItemsResponse result = favoriteService.list(USER_ID, FOLDER_ID);

    var summary = result.items().get(0).analysisSummary();
    assertThat(summary.hasTheme()).isTrue();
    assertThat(summary.appliedTheme().id()).isEqualTo(1L);
    assertThat(summary.appliedTheme().name()).isEqualTo("HBM/메모리");
    assertThat(summary.latestReportId()).isEqualTo(501L);
    assertThat(summary.expectedCar()).isEqualTo(6.8);
    assertThat(summary.message()).isNull();
    assertThat(result.items().get(0).unreadDisclosureCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("list()는 report 없이 primary theme만 있으면 has_theme=true에 report 필드는 null")
  void list_primaryThemeOnly_hasThemeTrueNoReport() throws Exception {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));

    Favorite fav = new Favorite(folder, "005930");
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of(fav));

    Stock samsung = stock("005930", "삼성전자");
    given(krxQuoteService.getQuotes(List.of("005930")))
        .willReturn(List.of(new KrxQuote("005930", 72000, 1.5, 1000)));
    given(stockRepository.findAllById(List.of("005930"))).willReturn(List.of(samsung));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(List.of("005930")))
        .willReturn(List.of(primaryStockTheme(samsung, theme(7L, "2차전지"))));
    given(analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(List.of("005930")))
        .willReturn(List.of());

    FolderItemsResponse result = favoriteService.list(USER_ID, FOLDER_ID);

    var summary = result.items().get(0).analysisSummary();
    assertThat(summary.hasTheme()).isTrue();
    assertThat(summary.appliedTheme().id()).isEqualTo(7L);
    assertThat(summary.latestReportId()).isNull();
    assertThat(summary.expectedCar()).isNull();
    assertThat(summary.message()).isNull();
  }

  @Test
  @DisplayName("list()는 universe 외 종목이면 has_theme=false와 message를 채운다")
  void list_universeOutside_hasThemeFalse() {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));

    Favorite fav = new Favorite(folder, "000660");
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of(fav));

    given(krxQuoteService.getQuotes(List.of("000660")))
        .willReturn(List.of(new KrxQuote("000660", 185000, -0.81, -1500)));
    given(stockRepository.findAllById(List.of("000660")))
        .willReturn(List.of(stock("000660", "SK하이닉스")));
    given(stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(List.of("000660")))
        .willReturn(List.of());
    given(analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(List.of("000660")))
        .willReturn(List.of());

    FolderItemsResponse result = favoriteService.list(USER_ID, FOLDER_ID);

    var summary = result.items().get(0).analysisSummary();
    assertThat(summary.hasTheme()).isFalse();
    assertThat(summary.message()).isEqualTo("ThemeSense 학습 universe 외 종목 — 테마 분류 대기 중");
    assertThat(summary.appliedTheme()).isNull();
    assertThat(result.items().get(0).unreadDisclosureCount()).isZero();
  }
}
