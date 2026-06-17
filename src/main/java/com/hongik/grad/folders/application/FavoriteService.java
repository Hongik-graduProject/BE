package com.hongik.grad.folders.application;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatus;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.dto.AnalysisSummary;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.folders.dto.FavoriteMoveRequest;
import com.hongik.grad.folders.dto.FavoriteMoveResponse;
import com.hongik.grad.folders.dto.FavoriteUpdateRequest;
import com.hongik.grad.folders.dto.FavoriteUpdateResponse;
import com.hongik.grad.folders.dto.FolderBriefResponse;
import com.hongik.grad.folders.dto.FolderItemsResponse;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.stocks.dto.KrxQuote;
import com.hongik.grad.themes.domain.StockTheme;
import com.hongik.grad.themes.domain.StockThemeRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

  private static final String UNIVERSE_OUTSIDE_MESSAGE = "ThemeSense 학습 universe 외 종목 — 테마 분류 대기 중";

  private final FavoriteRepository favoriteRepository;
  private final FolderRepository folderRepository;
  private final KrxQuoteService krxQuoteService;
  private final StockRepository stockRepository;
  private final StockThemeRepository stockThemeRepository;
  private final AnalysisReportRepository analysisReportRepository;
  private final ReportReadStatusRepository reportReadStatusRepository;

  public FavoriteService(
      FavoriteRepository favoriteRepository,
      FolderRepository folderRepository,
      KrxQuoteService krxQuoteService,
      StockRepository stockRepository,
      StockThemeRepository stockThemeRepository,
      AnalysisReportRepository analysisReportRepository,
      ReportReadStatusRepository reportReadStatusRepository) {
    this.favoriteRepository = favoriteRepository;
    this.folderRepository = folderRepository;
    this.krxQuoteService = krxQuoteService;
    this.stockRepository = stockRepository;
    this.stockThemeRepository = stockThemeRepository;
    this.analysisReportRepository = analysisReportRepository;
    this.reportReadStatusRepository = reportReadStatusRepository;
  }

  @Transactional(readOnly = true)
  public FolderItemsResponse list(UUID userId, Long folderId) {
    Folder folder = verifyFolderOwnership(userId, folderId);
    FolderBriefResponse header = FolderBriefResponse.from(folder);

    List<Favorite> favorites = favoriteRepository.findByFolderIdOrderByAddedAtDesc(folderId);
    if (favorites.isEmpty()) {
      return new FolderItemsResponse(header, List.of());
    }

    List<String> tickers = favorites.stream().map(Favorite::getTicker).distinct().toList();

    Map<String, KrxQuote> quoteMap =
        krxQuoteService.getQuotes(tickers).stream()
            .collect(Collectors.toMap(KrxQuote::ticker, Function.identity(), (a, b) -> a));

    Map<String, String> nameMap =
        stockRepository.findAllById(tickers).stream()
            .collect(Collectors.toMap(Stock::getTicker, Stock::getName, (a, b) -> a));

    Map<String, StockTheme> primaryThemeMap =
        stockThemeRepository.findByStockTickerInAndIsPrimaryTrue(tickers).stream()
            .collect(
                Collectors.toMap(
                    st -> st.getStock().getTicker(), Function.identity(), (a, b) -> a));

    // Latest report per ticker (input is ordered generatedAt desc → first seen wins).
    List<AnalysisReport> reports =
        analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(tickers);
    Map<String, AnalysisReport> latestReportMap = new HashMap<>();
    for (AnalysisReport report : reports) {
      latestReportMap.putIfAbsent(report.getStock().getTicker(), report);
    }

    Set<Long> readReportIds =
        reports.isEmpty()
            ? Set.of()
            : reportReadStatusRepository
                .findByUserIdAndReportIdIn(
                    userId, reports.stream().map(AnalysisReport::getId).toList())
                .stream()
                .map(ReportReadStatus::getReportId)
                .collect(Collectors.toSet());
    // Unread = reports for the ticker without a read-status row for this user.
    Map<String, Integer> unreadMap = new HashMap<>();
    for (AnalysisReport report : reports) {
      if (!readReportIds.contains(report.getId())) {
        unreadMap.merge(report.getStock().getTicker(), 1, Integer::sum);
      }
    }

    List<FavoriteItemResponse> items =
        favorites.stream()
            .map(
                fav -> {
                  String ticker = fav.getTicker();
                  AnalysisSummary summary =
                      buildSummary(latestReportMap.get(ticker), primaryThemeMap.get(ticker));
                  return FavoriteItemResponse.forList(
                      fav,
                      nameMap.get(ticker),
                      quoteMap.getOrDefault(ticker, KrxQuote.empty(ticker)),
                      unreadMap.getOrDefault(ticker, 0),
                      summary);
                })
            .toList();

    return new FolderItemsResponse(header, items);
  }

  private AnalysisSummary buildSummary(AnalysisReport latestReport, StockTheme primaryTheme) {
    if (latestReport != null) {
      return AnalysisSummary.withReport(
          new AnalysisSummary.AppliedTheme(
              latestReport.getAppliedTheme().getId(), latestReport.getAppliedTheme().getName()),
          latestReport.getId(),
          toDouble(latestReport.getExpectedCar()),
          latestReport.getSummary(),
          latestReport.getGeneratedAt());
    }
    if (primaryTheme != null) {
      return AnalysisSummary.themeOnly(
          new AnalysisSummary.AppliedTheme(
              primaryTheme.getTheme().getId(), primaryTheme.getTheme().getName()));
    }
    return AnalysisSummary.noTheme(UNIVERSE_OUTSIDE_MESSAGE);
  }

  private Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }

  /** §3-7 POST /folders/items — 종목을 폴더에 즐겨찾기로 등록. */
  @Transactional
  public com.hongik.grad.folders.dto.FavoriteAddResponse add(
      UUID userId, Long folderId, String ticker) {
    Folder folder = verifyFolderOwnership(userId, folderId);
    if (!stockRepository.existsById(ticker)) {
      throw new DomainException(ErrorCode.NOT_FOUND, "종목을 찾을 수 없습니다: " + ticker);
    }
    if (favoriteRepository.existsByFolderIdAndTicker(folderId, ticker)) {
      throw new DomainException(ErrorCode.DUPLICATE, "이미 폴더에 등록된 종목입니다");
    }
    Favorite saved = favoriteRepository.save(new Favorite(folder, ticker));
    return new com.hongik.grad.folders.dto.FavoriteAddResponse(
        saved.getId(), folder.getId(), saved.getTicker(), saved.getAddedAt());
  }

  @Transactional
  public FavoriteUpdateResponse update(UUID userId, Long itemId, FavoriteUpdateRequest request) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    if (request.memo() != null) {
      favorite.updateMemo(request.memo());
    }
    if (request.avgPrice() != null) {
      favorite.updateAvgPrice(request.avgPrice());
    }
    return new FavoriteUpdateResponse(favorite.getId(), favorite.getMemo(), favorite.getAvgPrice());
  }

  @Transactional
  public void delete(UUID userId, Long itemId) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    favoriteRepository.delete(favorite);
  }

  @Transactional
  public FavoriteMoveResponse move(UUID userId, Long itemId, FavoriteMoveRequest request) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    Folder target =
        folderRepository
            .findByIdAndUserId(request.targetFolderId(), userId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "이동할 폴더를 찾을 수 없습니다"));
    if (favoriteRepository.existsByFolderIdAndTickerAndIdNot(
        target.getId(), favorite.getTicker(), itemId)) {
      throw new DomainException(ErrorCode.DUPLICATE, "대상 폴더에 이미 동일한 종목이 존재합니다");
    }
    Long fromFolderId = favorite.getFolder().getId();
    favorite.moveToFolder(target);
    return new FavoriteMoveResponse(favorite.getId(), fromFolderId, target.getId());
  }

  private Folder verifyFolderOwnership(UUID userId, Long folderId) {
    Folder folder =
        folderRepository
            .findById(folderId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다"));
    if (!folder.getUserId().equals(userId)) {
      throw new DomainException(ErrorCode.FORBIDDEN, "해당 폴더에 접근 권한이 없습니다");
    }
    return folder;
  }

  private Favorite findOwnedFavorite(UUID userId, Long itemId) {
    Favorite favorite =
        favoriteRepository
            .findById(itemId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "즐겨찾기 항목을 찾을 수 없습니다"));
    if (!favorite.getFolder().getUserId().equals(userId)) {
      throw new DomainException(ErrorCode.FORBIDDEN, "해당 항목에 접근 권한이 없습니다");
    }
    return favorite;
  }
}
