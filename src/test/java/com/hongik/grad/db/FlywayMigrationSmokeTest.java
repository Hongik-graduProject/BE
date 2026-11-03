package com.hongik.grad.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.support.TestcontainersConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies every Flyway migration applies cleanly against a real Postgres container and leaves the
 * expected schema: no placeholder stocks (V7 + V10) and local credential columns on profiles (V9).
 */
@SpringBootTest
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class FlywayMigrationSmokeTest {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName(
      "전체 마이그레이션(V12까지) 적용 후 17 테이블 / 8 ENUM / 15 metric / placeholder stock 0 / profiles 자격증명 컬럼")
  void flywayMigratesCleanly() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                + "AND table_name <> 'flyway_schema_history'",
            Integer.class);
    assertThat(tableCount).as("public schema 의 도메인 테이블 수").isEqualTo(17);

    Integer enumTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM pg_type WHERE typtype = 'e'", Integer.class);
    assertThat(enumTypeCount).as("CREATE TYPE … AS ENUM 으로 정의된 타입 수").isEqualTo(8);

    List<String> expectedEnums =
        List.of(
            "direction_type",
            "disclosure_type",
            "folder_type",
            "market_type",
            "metric_category",
            "notification_type",
            "period_type",
            "platform_type");
    List<String> actualEnums =
        jdbc.queryForList(
            "SELECT typname FROM pg_type WHERE typtype = 'e' ORDER BY typname", String.class);
    assertThat(actualEnums).as("ENUM 타입 이름 목록").containsExactlyInAnyOrderElementsOf(expectedEnums);

    Integer metricCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM financial_metric_catalog", Integer.class);
    assertThat(metricCount).as("V2 seed 의 metric 행 수").isEqualTo(15);

    List<String> sampleCodes =
        jdbc.queryForList(
            "SELECT metric_code FROM financial_metric_catalog "
                + "WHERE metric_code IN ('per','pbr','roe','debt_ratio','market_cap')",
            String.class);
    Set<String> expected = new HashSet<>(List.of("per", "pbr", "roe", "debt_ratio", "market_cap"));
    assertThat(new HashSet<>(sampleCodes)).as("핵심 metric_code 5개 모두 존재").isEqualTo(expected);

    String latestVersion =
        jdbc.queryForObject(
            "SELECT version FROM flyway_schema_history WHERE success "
                + "ORDER BY installed_rank DESC LIMIT 1",
            String.class);
    assertThat(latestVersion).as("최신 Flyway 버전").isEqualTo("12");

    Integer placeholderCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM stocks WHERE ticker LIKE 'K%' OR ticker LIKE 'Q%'",
            Integer.class);
    assertThat(placeholderCount).as("V3 placeholder stock 은 V7 + V10 에서 모두 삭제").isZero();

    List<String> profileColumns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = 'profiles'",
            String.class);
    assertThat(profileColumns).as("V9 profiles 자격증명 컬럼").contains("email", "password_hash");

    Integer emailIndexCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes "
                + "WHERE schemaname = 'public' AND tablename = 'profiles' "
                + "AND indexname = 'uk_profiles_email'",
            Integer.class);
    assertThat(emailIndexCount).as("V9 uk_profiles_email 인덱스").isEqualTo(1);

    List<String> financialUniqueKeys =
        jdbc.queryForList(
            "SELECT indexname FROM pg_indexes "
                + "WHERE schemaname = 'public' AND tablename = 'financial_statements' "
                + "AND indexdef LIKE 'CREATE UNIQUE INDEX%' AND indexname <> 'pk_financial_statements'",
            String.class);
    assertThat(financialUniqueKeys)
        .as("V11: 재무제표 고유 키는 sj_div 포함 키 하나뿐")
        .containsExactly("uq_financial_statements_ticker_period_type_period_sj");
  }
}
