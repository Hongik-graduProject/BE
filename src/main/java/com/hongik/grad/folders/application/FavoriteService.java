package com.hongik.grad.folders.application;

import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.folders.dto.FavoriteMoveRequest;
import com.hongik.grad.folders.dto.FavoriteUpdateRequest;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final FolderRepository folderRepository;
  private final KrxQuoteService krxQuoteService;

  public FavoriteService(
      FavoriteRepository favoriteRepository,
      FolderRepository folderRepository,
      KrxQuoteService krxQuoteService) {
    this.favoriteRepository = favoriteRepository;
    this.folderRepository = folderRepository;
    this.krxQuoteService = krxQuoteService;
  }

  public List<FavoriteItemResponse> list(UUID userId, Long folderId) {
    verifyFolderOwnership(userId, folderId);
    List<Favorite> favorites = favoriteRepository.findByFolderIdOrderByAddedAtDesc(folderId);
    if (favorites.isEmpty()) {
      return List.of();
    }
    List<String> tickers = favorites.stream().map(Favorite::getTicker).toList();
    Map<String, KrxQuote> quoteMap =
        krxQuoteService.getQuotes(tickers).stream()
            .collect(Collectors.toMap(KrxQuote::ticker, Function.identity(), (a, b) -> a));
    return favorites.stream()
        .map(
            fav ->
                FavoriteItemResponse.from(
                    fav, quoteMap.getOrDefault(fav.getTicker(), KrxQuote.empty(fav.getTicker()))))
        .toList();
  }

  @Transactional
  public FavoriteItemResponse update(UUID userId, Long itemId, FavoriteUpdateRequest request) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    if (request.memo() != null) {
      favorite.updateMemo(request.memo());
    }
    if (request.avgPrice() != null) {
      favorite.updateAvgPrice(request.avgPrice());
    }
    return FavoriteItemResponse.from(favorite);
  }

  @Transactional
  public void delete(UUID userId, Long itemId) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    favoriteRepository.delete(favorite);
  }

  @Transactional
  public FavoriteItemResponse move(UUID userId, Long itemId, FavoriteMoveRequest request) {
    Favorite favorite = findOwnedFavorite(userId, itemId);
    Folder target =
        folderRepository
            .findByIdAndUserId(request.targetFolderId(), userId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "이동할 폴더를 찾을 수 없습니다"));
    if (favoriteRepository.existsByFolderIdAndTickerAndIdNot(
        target.getId(), favorite.getTicker(), itemId)) {
      throw new DomainException(ErrorCode.DUPLICATE, "대상 폴더에 이미 동일한 종목이 존재합니다");
    }
    favorite.moveToFolder(target);
    return FavoriteItemResponse.from(favorite);
  }

  private void verifyFolderOwnership(UUID userId, Long folderId) {
    Folder folder =
        folderRepository
            .findById(folderId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "폴더를 찾을 수 없습니다"));
    if (!folder.getUserId().equals(userId)) {
      throw new DomainException(ErrorCode.FORBIDDEN, "해당 폴더에 접근 권한이 없습니다");
    }
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
