package com.hongik.grad.folders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hongik.grad.folders.application.FavoriteService;
import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.folders.domain.FolderType;
import com.hongik.grad.folders.dto.FavoriteItemResponse;
import com.hongik.grad.stocks.application.KrxQuoteService;
import com.hongik.grad.stocks.dto.KrxQuote;
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

  @InjectMocks private FavoriteService favoriteService;

  private static final UUID USER_ID = UUID.randomUUID();
  private static final Long FOLDER_ID = 1L;

  @Test
  @DisplayName("list()는 즐겨찾기 항목에 시세 정보를 포함하여 반환한다")
  void list_returnsItemsWithPrices() {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));

    Favorite fav = new Favorite(folder, "005930");
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of(fav));

    KrxQuote quote = new KrxQuote("005930", 72000, 1.5, 1000);
    given(krxQuoteService.getQuotes(List.of("005930"))).willReturn(List.of(quote));

    List<FavoriteItemResponse> result = favoriteService.list(USER_ID, FOLDER_ID);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).ticker()).isEqualTo("005930");
    assertThat(result.get(0).currentPrice()).isEqualTo(72000L);
  }

  @Test
  @DisplayName("list()는 즐겨찾기가 비어있으면 빈 리스트를 반환한다")
  void list_emptyFavorites_returnsEmptyList() {
    Folder folder = new Folder(USER_ID, "테스트", "#000000", FolderType.CUSTOM, 0);
    given(folderRepository.findById(FOLDER_ID)).willReturn(Optional.of(folder));
    given(favoriteRepository.findByFolderIdOrderByAddedAtDesc(FOLDER_ID)).willReturn(List.of());

    List<FavoriteItemResponse> result = favoriteService.list(USER_ID, FOLDER_ID);

    assertThat(result).isEmpty();
  }
}
