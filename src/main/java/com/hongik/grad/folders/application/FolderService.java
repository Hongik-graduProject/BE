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
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.folders.dto.CreateFolderRequest;
import com.hongik.grad.folders.dto.FolderListResponse;
import com.hongik.grad.folders.dto.FolderResponse;
import com.hongik.grad.folders.dto.FolderUpdateResponse;
import com.hongik.grad.folders.dto.ReorderFoldersRequest;
import com.hongik.grad.folders.dto.UpdateFolderRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

  private final FolderRepository folderRepository;
  private final FavoriteRepository favoriteRepository;
  private final AnalysisReportRepository analysisReportRepository;
  private final ReportReadStatusRepository reportReadStatusRepository;

  public FolderService(
      FolderRepository folderRepository,
      FavoriteRepository favoriteRepository,
      AnalysisReportRepository analysisReportRepository,
      ReportReadStatusRepository reportReadStatusRepository) {
    this.folderRepository = folderRepository;
    this.favoriteRepository = favoriteRepository;
    this.analysisReportRepository = analysisReportRepository;
    this.reportReadStatusRepository = reportReadStatusRepository;
  }

  @Transactional(readOnly = true)
  public List<FolderListResponse> list(UUID userId) {
    List<Folder> folders = folderRepository.findByUserIdOrderByDisplayOrderAsc(userId);
    List<Favorite> favorites = favoriteRepository.findByFolderUserId(userId);

    Map<Long, Integer> itemCountByFolder = new HashMap<>();
    for (Favorite fav : favorites) {
      itemCountByFolder.merge(fav.getFolder().getId(), 1, Integer::sum);
    }

    Map<String, Integer> unreadByTicker = unreadCountByTicker(userId, favorites);
    Map<Long, Integer> unreadByFolder = new HashMap<>();
    for (Favorite fav : favorites) {
      int unread = unreadByTicker.getOrDefault(fav.getTicker(), 0);
      if (unread > 0) {
        unreadByFolder.merge(fav.getFolder().getId(), unread, Integer::sum);
      }
    }

    return folders.stream()
        .map(
            f ->
                FolderListResponse.from(
                    f,
                    itemCountByFolder.getOrDefault(f.getId(), 0),
                    unreadByFolder.getOrDefault(f.getId(), 0)))
        .toList();
  }

  /** Per-ticker unread = analysis_reports without a report_read_status row for this user (§3-6). */
  private Map<String, Integer> unreadCountByTicker(UUID userId, List<Favorite> favorites) {
    List<String> tickers = favorites.stream().map(Favorite::getTicker).distinct().toList();
    if (tickers.isEmpty()) {
      return Map.of();
    }

    List<AnalysisReport> reports =
        analysisReportRepository.findByStockTickerInOrderByGeneratedAtDesc(tickers);
    if (reports.isEmpty()) {
      return Map.of();
    }

    Set<Long> readReportIds =
        reportReadStatusRepository
            .findByUserIdAndReportIdIn(userId, reports.stream().map(AnalysisReport::getId).toList())
            .stream()
            .map(ReportReadStatus::getReportId)
            .collect(Collectors.toSet());

    Map<String, Integer> unreadByTicker = new HashMap<>();
    for (AnalysisReport report : reports) {
      if (!readReportIds.contains(report.getId())) {
        unreadByTicker.merge(report.getStock().getTicker(), 1, Integer::sum);
      }
    }
    return unreadByTicker;
  }

  @Transactional
  public FolderResponse create(UUID userId, CreateFolderRequest request) {
    if (folderRepository.existsByUserIdAndNameAndType(userId, request.name(), FolderType.CUSTOM)) {
      throw new DomainException(ErrorCode.DUPLICATE, "같은 이름의 폴더가 이미 존재합니다");
    }
    int nextOrder = folderRepository.countByUserId(userId);
    Folder folder =
        new Folder(userId, request.name(), request.color(), FolderType.CUSTOM, nextOrder);
    return FolderResponse.from(folderRepository.save(folder));
  }

  @Transactional
  public FolderUpdateResponse update(UUID userId, Long folderId, UpdateFolderRequest request) {
    Folder folder = findOwnedFolder(userId, folderId);
    if (folder.getType() == FolderType.DEFAULT) {
      throw new DomainException(ErrorCode.FORBIDDEN, "기본 폴더는 수정할 수 없습니다");
    }
    boolean nameConflict =
        folderRepository.existsByUserIdAndNameAndTypeAndIdNot(
            userId, request.name(), FolderType.CUSTOM, folderId);
    if (nameConflict) {
      throw new DomainException(ErrorCode.DUPLICATE, "같은 이름의 폴더가 이미 존재합니다");
    }
    folder.update(request.name(), request.color());
    return FolderUpdateResponse.from(folder);
  }

  @Transactional
  public void delete(UUID userId, Long folderId) {
    Folder folder = findOwnedFolder(userId, folderId);
    if (folder.getType() == FolderType.DEFAULT) {
      throw new DomainException(ErrorCode.FORBIDDEN, "기본 폴더는 삭제할 수 없습니다");
    }
    folderRepository.delete(folder);
  }

  @Transactional
  public void reorder(UUID userId, ReorderFoldersRequest request) {
    List<Long> ids = request.folderIds();
    List<Folder> folders = folderRepository.findAllByUserIdAndIdIn(userId, ids);
    if (folders.size() != ids.size()) {
      throw new DomainException(ErrorCode.FORBIDDEN, "본인 폴더만 재정렬할 수 있습니다");
    }
    for (int i = 0; i < ids.size(); i++) {
      Long id = ids.get(i);
      Folder folder =
          folders.stream()
              .filter(f -> f.getId().equals(id))
              .findFirst()
              .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다: " + id));
      folder.setDisplayOrder(i);
    }
  }

  private Folder findOwnedFolder(UUID userId, Long folderId) {
    Folder folder =
        folderRepository
            .findById(folderId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다"));
    if (!folder.getUserId().equals(userId)) {
      throw new DomainException(ErrorCode.FORBIDDEN, "해당 폴더에 접근 권한이 없습니다");
    }
    return folder;
  }
}
