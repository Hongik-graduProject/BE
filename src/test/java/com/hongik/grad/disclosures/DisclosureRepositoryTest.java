package com.hongik.grad.disclosures;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.disclosures.domain.Disclosure;
import com.hongik.grad.disclosures.domain.DisclosureRepository;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
class DisclosureRepositoryTest {

  @Autowired private DisclosureRepository disclosureRepository;
  @Autowired private StockRepository stockRepository;
  @Autowired private TestEntityManager em;

  private Stock stock;

  @BeforeEach
  void setUp() {
    stock = stockRepository.save(new Stock("005930", "삼성전자", Market.KOSPI));
    em.flush();
    em.clear();
    stock = stockRepository.findById("005930").orElseThrow();
  }

  @Test
  @DisplayName("Disclosure 저장 시 IDENTITY PK와 createdAt 타임스탬프가 채워진다")
  void saveAndRead() {
    OffsetDateTime filedAt = OffsetDateTime.of(2025, 3, 15, 9, 0, 0, 0, ZoneOffset.UTC);
    Disclosure disclosure =
        new Disclosure(stock, "20250315000001", "사업보고서", DisclosureType.REPORT, filedAt);

    Disclosure saved = disclosureRepository.save(disclosure);
    em.flush();
    em.clear();

    Disclosure found = disclosureRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getId()).isNotNull();
    assertThat(found.getStock().getTicker()).isEqualTo("005930");
    assertThat(found.getDartFilingId()).isEqualTo("20250315000001");
    assertThat(found.getTitle()).isEqualTo("사업보고서");
    assertThat(found.getType()).isEqualTo(DisclosureType.REPORT);
    assertThat(found.getFiledAt()).isEqualTo(filedAt);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("dart_filing_id 유니크 제약으로 중복 저장이 방지된다")
  void uniqueDartFilingId() {
    OffsetDateTime filedAt = OffsetDateTime.of(2025, 3, 15, 9, 0, 0, 0, ZoneOffset.UTC);
    disclosureRepository.save(
        new Disclosure(stock, "20250315000002", "분기보고서", DisclosureType.QUARTERLY, filedAt));
    em.flush();
    em.clear();

    assertThat(disclosureRepository.existsByDartFilingId("20250315000002")).isTrue();
    assertThat(disclosureRepository.existsByDartFilingId("nonexistent")).isFalse();
  }

  @Test
  @DisplayName("dart_filing_id로 Disclosure를 조회할 수 있다")
  void findByDartFilingId() {
    OffsetDateTime filedAt = OffsetDateTime.of(2025, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    disclosureRepository.save(
        new Disclosure(stock, "20250601000001", "주요사항보고서", DisclosureType.EVENT, filedAt));
    em.flush();
    em.clear();

    Optional<Disclosure> found = disclosureRepository.findByDartFilingId("20250601000001");
    assertThat(found).isPresent();
    assertThat(found.get().getTitle()).isEqualTo("주요사항보고서");
    assertThat(found.get().getType()).isEqualTo(DisclosureType.EVENT);

    assertThat(disclosureRepository.findByDartFilingId("nonexistent")).isEmpty();
  }

  @Test
  @DisplayName("DisclosureType 컨버터가 DB 값을 올바르게 변환한다")
  void disclosureTypeConversion() {
    OffsetDateTime filedAt = OffsetDateTime.of(2025, 9, 30, 9, 0, 0, 0, ZoneOffset.UTC);
    disclosureRepository.save(
        new Disclosure(stock, "20250930000001", "반기보고서", DisclosureType.INTERIM, filedAt));
    em.flush();
    em.clear();

    Disclosure found = disclosureRepository.findByDartFilingId("20250930000001").orElseThrow();
    assertThat(found.getType()).isEqualTo(DisclosureType.INTERIM);
  }
}
