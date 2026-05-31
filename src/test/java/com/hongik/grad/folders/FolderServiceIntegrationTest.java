package com.hongik.grad.folders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hongik.grad.analysis.domain.AnalysisReport;
import com.hongik.grad.analysis.domain.AnalysisReportRepository;
import com.hongik.grad.analysis.domain.ReportReadStatus;
import com.hongik.grad.analysis.domain.ReportReadStatusRepository;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.folders.application.FolderService;
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
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.themes.domain.Theme;
import com.hongik.grad.themes.domain.ThemeRepository;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
@Transactional
class FolderServiceIntegrationTest {

  @Autowired private FolderService folderService;
  @Autowired private FolderRepository folderRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private ThemeRepository themeRepository;
  @Autowired private AnalysisReportRepository analysisReportRepository;
  @Autowired private ReportReadStatusRepository reportReadStatusRepository;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    profileRepository.save(new Profile(userId, "테스트유저"));
    folderRepository.save(Folder.defaultFor(userId));
  }

  @Test
  @DisplayName("create: 커스텀 폴더 생성 시 displayOrder 가 기존 폴더 수로 설정된다")
  void create_setsDisplayOrder() {
    FolderResponse response =
        folderService.create(userId, new CreateFolderRequest("관심1", "#FF0000"));

    assertThat(response.name()).isEqualTo("관심1");
    assertThat(response.color()).isEqualTo("#FF0000");
    assertThat(response.type()).isEqualTo(FolderType.CUSTOM);
    assertThat(response.displayOrder()).isEqualTo(1); // default folder is 0
  }

  @Test
  @DisplayName("create: 동일 이름 중복 시 DUPLICATE 예외가 발생한다")
  void create_duplicate_throws() {
    folderService.create(userId, new CreateFolderRequest("중복이름", "#FF0000"));

    assertThatThrownBy(
            () -> folderService.create(userId, new CreateFolderRequest("중복이름", "#00FF00")))
        .isInstanceOf(DomainException.class)
        .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.DUPLICATE));
  }

  @Test
  @DisplayName("update: 이름과 색상이 변경된다")
  void update_changesNameAndColor() {
    FolderResponse created =
        folderService.create(userId, new CreateFolderRequest("기존이름", "#FF0000"));

    FolderUpdateResponse updated =
        folderService.update(userId, created.id(), new UpdateFolderRequest("새이름", "#0000FF"));

    assertThat(updated.name()).isEqualTo("새이름");
    assertThat(updated.color()).isEqualTo("#0000FF");
  }

  @Test
  @DisplayName("update: 기본 폴더 수정 시 FORBIDDEN 예외가 발생한다")
  void update_defaultFolder_throws() {
    Folder defaultFolder =
        folderRepository.findByUserIdAndType(userId, FolderType.DEFAULT).orElseThrow();

    assertThatThrownBy(
            () ->
                folderService.update(
                    userId, defaultFolder.getId(), new UpdateFolderRequest("새이름", "#0000FF")))
        .isInstanceOf(DomainException.class)
        .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  @DisplayName("update: 다른 유저의 폴더 수정 시 FORBIDDEN 예외가 발생한다")
  void update_otherUserFolder_throws() {
    UUID otherId = UUID.randomUUID();
    profileRepository.save(new Profile(otherId, "타유저"));
    FolderResponse other =
        folderService.create(otherId, new CreateFolderRequest("타유저폴더", "#FF0000"));

    assertThatThrownBy(
            () ->
                folderService.update(userId, other.id(), new UpdateFolderRequest("해킹", "#000000")))
        .isInstanceOf(DomainException.class)
        .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  @DisplayName("delete: 커스텀 폴더가 삭제된다")
  void delete_customFolder() {
    FolderResponse created =
        folderService.create(userId, new CreateFolderRequest("삭제대상", "#FF0000"));

    folderService.delete(userId, created.id());

    assertThat(folderRepository.findById(created.id())).isEmpty();
  }

  @Test
  @DisplayName("delete: 기본 폴더 삭제 시 FORBIDDEN 예외가 발생한다")
  void delete_defaultFolder_throws() {
    Folder defaultFolder =
        folderRepository.findByUserIdAndType(userId, FolderType.DEFAULT).orElseThrow();

    assertThatThrownBy(() -> folderService.delete(userId, defaultFolder.getId()))
        .isInstanceOf(DomainException.class)
        .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
  }

  @Test
  @DisplayName("reorder: folderIds 순서대로 displayOrder 가 갱신된다")
  void reorder_updatesDisplayOrder() {
    FolderResponse f1 = folderService.create(userId, new CreateFolderRequest("폴더1", "#FF0000"));
    FolderResponse f2 = folderService.create(userId, new CreateFolderRequest("폴더2", "#00FF00"));
    Folder defaultFolder =
        folderRepository.findByUserIdAndType(userId, FolderType.DEFAULT).orElseThrow();

    List<Long> newOrder = List.of(f2.id(), f1.id(), defaultFolder.getId());
    folderService.reorder(userId, new ReorderFoldersRequest(newOrder));

    assertThat(folderRepository.findById(f2.id()).orElseThrow().getDisplayOrder()).isEqualTo(0);
    assertThat(folderRepository.findById(f1.id()).orElseThrow().getDisplayOrder()).isEqualTo(1);
    assertThat(folderRepository.findById(defaultFolder.getId()).orElseThrow().getDisplayOrder())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("list: 폴더 unread_disclosure_count = 폴더 내 종목들의 미확인 보고서 수 합")
  void list_aggregatesUnreadDisclosureCountPerFolder() {
    FolderResponse custom = folderService.create(userId, new CreateFolderRequest("반도체", "#4A90D9"));
    Folder customFolder = folderRepository.findById(custom.id()).orElseThrow();

    Stock samsung = stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
    Stock skHynix = stockRepository.save(new Stock("000660", "SK하이닉스", Market.KOSPI));
    Theme theme = saveTheme("HBM/메모리");

    favoriteRepository.save(new Favorite(customFolder, "005930"));
    favoriteRepository.save(new Favorite(customFolder, "000660"));

    AnalysisReport samsungRead =
        analysisReportRepository.save(new AnalysisReport(samsung, theme, "삼성 read"));
    analysisReportRepository.save(new AnalysisReport(samsung, theme, "삼성 unread"));
    analysisReportRepository.save(new AnalysisReport(skHynix, theme, "SK unread"));
    reportReadStatusRepository.save(new ReportReadStatus(userId, samsungRead.getId()));

    List<FolderListResponse> result = folderService.list(userId);

    FolderListResponse customResult =
        result.stream().filter(f -> f.id().equals(custom.id())).findFirst().orElseThrow();
    assertThat(customResult.itemCount()).isEqualTo(2);
    // samsung: 2 reports - 1 read = 1 unread; sk: 1 unread -> folder sum = 2
    assertThat(customResult.unreadDisclosureCount()).isEqualTo(2);

    FolderListResponse defaultResult =
        result.stream().filter(f -> f.type() == FolderType.DEFAULT).findFirst().orElseThrow();
    assertThat(defaultResult.itemCount()).isZero();
    assertThat(defaultResult.unreadDisclosureCount()).isZero();
  }

  @Test
  @DisplayName("list: 종목 보고서가 모두 읽음이면 unread_disclosure_count = 0")
  void list_allReportsRead_unreadZero() {
    FolderResponse custom = folderService.create(userId, new CreateFolderRequest("테크", "#FF0000"));
    Folder customFolder = folderRepository.findById(custom.id()).orElseThrow();

    Stock samsung = stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
    Theme theme = saveTheme("HBM/메모리");
    favoriteRepository.save(new Favorite(customFolder, "005930"));

    AnalysisReport report =
        analysisReportRepository.save(new AnalysisReport(samsung, theme, "삼성 read"));
    reportReadStatusRepository.save(new ReportReadStatus(userId, report.getId()));

    List<FolderListResponse> result = folderService.list(userId);

    FolderListResponse customResult =
        result.stream().filter(f -> f.id().equals(custom.id())).findFirst().orElseThrow();
    assertThat(customResult.itemCount()).isEqualTo(1);
    assertThat(customResult.unreadDisclosureCount()).isZero();
  }

  private Theme saveTheme(String name) {
    Theme theme = new Theme(name);
    theme.setSource("test");
    return themeRepository.save(theme);
  }

  @Test
  @DisplayName("reorder: 다른 유저의 폴더 포함 시 FORBIDDEN 예외가 발생한다")
  void reorder_otherUserFolder_throws() {
    UUID otherId = UUID.randomUUID();
    profileRepository.save(new Profile(otherId, "타유저"));
    FolderResponse otherFolder =
        folderService.create(otherId, new CreateFolderRequest("타유저폴더", "#FF0000"));
    Folder defaultFolder =
        folderRepository.findByUserIdAndType(userId, FolderType.DEFAULT).orElseThrow();

    assertThatThrownBy(
            () ->
                folderService.reorder(
                    userId,
                    new ReorderFoldersRequest(List.of(defaultFolder.getId(), otherFolder.id()))))
        .isInstanceOf(DomainException.class)
        .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
  }
}
