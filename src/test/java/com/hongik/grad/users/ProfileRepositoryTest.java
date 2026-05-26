package com.hongik.grad.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.support.TestcontainersConfig;
import com.hongik.grad.users.domain.Profile;
import com.hongik.grad.users.domain.ProfileRepository;
import java.util.UUID;
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
class ProfileRepositoryTest {

  @Autowired private ProfileRepository repository;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("Profile 저장 시 Supabase 발급 UUID PK + BaseTimeEntity 타임스탬프가 채워진다")
  void saveAndRead() {
    UUID id = UUID.randomUUID();
    Profile saved = repository.save(new Profile(id, "에밀"));

    em.flush();
    em.clear();

    Profile found = repository.findById(id).orElseThrow();
    assertThat(found.getId()).isEqualTo(id);
    assertThat(found.getNickname()).isEqualTo("에밀");
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(found.getDeletedAt()).isNull();
    assertThat(saved.getId()).isEqualTo(id);
  }

  @Test
  @DisplayName("updateNickname 호출 후 영속 컨텍스트 flush 시 nickname 이 갱신된다")
  void updateNickname() {
    UUID id = UUID.randomUUID();
    Profile saved = repository.save(new Profile(id, "before"));
    em.flush();
    em.clear();

    Profile reloaded = repository.findById(saved.getId()).orElseThrow();
    reloaded.updateNickname("after");
    em.flush();
    em.clear();

    assertThat(repository.findById(id).orElseThrow().getNickname()).isEqualTo("after");
  }
}
