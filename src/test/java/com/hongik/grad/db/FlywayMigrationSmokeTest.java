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
 * Verifies V1/V2/V3 Flyway migrations apply cleanly against a real Postgres container and produce
 * the expected schema + seed counts described in PR-02.
 */
@SpringBootTest
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class FlywayMigrationSmokeTest {

  @Autowired private DataSource dataSource;

  @Test
  @DisplayName("V1+V2+V3 적용 후 17 테이블 / 8 ENUM / 15 metric / 350 stock 이 생성된다")
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

    Integer stockCount = jdbc.queryForObject("SELECT COUNT(*) FROM stocks", Integer.class);
    assertThat(stockCount).as("V3 seed 의 stock 총 행 수").isEqualTo(350);

    Integer kospiCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM stocks WHERE market = 'KOSPI'", Integer.class);
    Integer kosdaqCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM stocks WHERE market = 'KOSDAQ'", Integer.class);
    assertThat(kospiCount).as("KOSPI 행 수").isEqualTo(200);
    assertThat(kosdaqCount).as("KOSDAQ 행 수").isEqualTo(150);
    assertThat(kospiCount + kosdaqCount).as("KOSPI + KOSDAQ 합계").isEqualTo(350);
  }
}
