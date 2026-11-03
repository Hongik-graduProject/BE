package com.hongik.grad.folders;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.folders.domain.Favorite;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.folders.domain.Folder;
import com.hongik.grad.folders.domain.FolderRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class FavoriteRepositoryTest {

  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private FolderRepository folderRepository;
  @Autowired private ProfileRepository profileRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private TestEntityManager em;

  private Folder folder;

  @BeforeEach
  void setUp() {
    // V7 deletes the K* seed placeholders, so the favorites FK targets are created here.
    stockRepository.save(new Stock("K00001", "테스트종목1", Market.KOSPI));
    stockRepository.save(new Stock("K00002", "테스트종목2", Market.KOSPI));
    UUID userId = UUID.randomUUID();
    profileRepository.save(new Profile(userId, "테스트유저"));
    folder = folderRepository.save(Folder.defaultFor(userId));
    em.flush();
    em.clear();
    folder = folderRepository.findById(folder.getId()).orElseThrow();
  }

  @Test
  @DisplayName("Favorite 저장 시 id와 addedAt 이 채워진다")
  void saveAndRead() {
    Favorite saved = favoriteRepository.save(new Favorite(folder, "K00001"));
    em.flush();
    em.clear();

    Favorite found = favoriteRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getTicker()).isEqualTo("K00001");
    assertThat(found.getMemo()).isNull();
    assertThat(found.getAvgPrice()).isNull();
    assertThat(found.getAddedAt()).isNotNull();
    assertThat(found.getFolder().getId()).isEqualTo(folder.getId());
  }

  @Test
  @DisplayName("findByFolderIdOrderByAddedAtDesc 는 해당 폴더의 즐겨찾기를 최신순으로 반환한다")
  void findByFolderIdOrderByAddedAtDesc() {
    favoriteRepository.save(new Favorite(folder, "K00001"));
    favoriteRepository.save(new Favorite(folder, "K00002"));
    em.flush();
    em.clear();

    List<Favorite> result = favoriteRepository.findByFolderIdOrderByAddedAtDesc(folder.getId());
    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(Favorite::getTicker)
        .containsExactlyInAnyOrder("K00001", "K00002");
  }

  @Test
  @DisplayName("existsByFolderIdAndTicker 는 동일 폴더+티커 중복 여부를 반환한다")
  void existsByFolderIdAndTicker() {
    favoriteRepository.save(new Favorite(folder, "K00001"));
    em.flush();
    em.clear();

    assertThat(favoriteRepository.existsByFolderIdAndTicker(folder.getId(), "K00001")).isTrue();
    assertThat(favoriteRepository.existsByFolderIdAndTicker(folder.getId(), "K00002")).isFalse();
  }

  @Test
  @DisplayName("updateMemo 후 flush 시 memo 가 갱신된다")
  void updateMemo() {
    Favorite saved = favoriteRepository.save(new Favorite(folder, "K00001"));
    em.flush();
    em.clear();

    Favorite reloaded = favoriteRepository.findById(saved.getId()).orElseThrow();
    reloaded.updateMemo("메모 테스트");
    em.flush();
    em.clear();

    assertThat(favoriteRepository.findById(saved.getId()).orElseThrow().getMemo())
        .isEqualTo("메모 테스트");
  }

  @Test
  @DisplayName("findByIdAndFolderId 는 폴더 소유권 검증에 사용된다")
  void findByIdAndFolderId() {
    Favorite saved = favoriteRepository.save(new Favorite(folder, "K00001"));
    em.flush();
    em.clear();

    assertThat(favoriteRepository.findByIdAndFolderId(saved.getId(), folder.getId())).isPresent();
    assertThat(favoriteRepository.findByIdAndFolderId(saved.getId(), folder.getId() + 999))
        .isEmpty();
  }
}
