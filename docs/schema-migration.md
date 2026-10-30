# Schema Migration: MySQL → Postgres

> **갱신 안내 (2026-10-30).** 이 문서는 PR-02 당시의 기록이라 Supabase 를 전제로
> 쓰여 있다. 이후 Supabase 의존성을 전부 제거하고 로컬 Postgres 기반 자체 인증으로
> 옮겼다. 아래 본문에서 Supabase 를 언급하는 대목은 당시 의사결정의 배경으로만
> 읽을 것. 현재 상태는 [`handoff-ml-integration.md`](handoff-ml-integration.md) §8 에
> 있다. 특히 두 가지가 바뀌었다. `profiles.id` 는 이제 백엔드가 생성하는 UUID 이고,
> refresh 토큰은 `typ=refresh` 클레임을 가진 무상태 JWT 다. §1 의 타입 변환표와
> §2 의 Flyway 운영 규칙은 그대로 유효하다.

> 본 문서는 PR-02 산출물이다. `docs/erdcloud_schema.sql` (MySQL DDL) 를 Postgres 로
> 어떻게 옮겼는지, Flyway 운영 규칙은 무엇인지, JPA/Hibernate 매핑은 어떻게 잡는지를
> 한 곳에 정리한다. 이후 V_n+1 마이그레이션을 추가하는 모든 PR 은 이 문서를 따른다.

---

## 1. MySQL → Postgres 타입 변환 표

| MySQL 원본 | Postgres 채택 | 채택 이유 |
|---|---|---|
| `BIGINT AUTO_INCREMENT` | `BIGINT GENERATED ALWAYS AS IDENTITY` | SQL 표준. 시퀀스를 명시적으로 노출하지 않아 권한 관리가 단순. |
| `INT AUTO_INCREMENT` | `INT GENERATED ALWAYS AS IDENTITY` | 동일 이유. |
| `BIGINT` (PK, `users.id`) | `UUID` (`profiles.id`) | Supabase `auth.users.id` 가 UUID. 백엔드는 발급자가 아니므로 generator 없음. |
| `DATETIME` | `TIMESTAMPTZ` | Supabase 표준이 UTC 보관. Hibernate `time_zone=UTC` 와 일관. |
| `DATETIME … ON UPDATE CURRENT_TIMESTAMP` | `TIMESTAMPTZ` + JPA `@UpdateTimestamp` | Postgres 는 컬럼 ON UPDATE 미지원. 갱신은 ORM 책임. |
| `TINYINT(1)` | `BOOLEAN` | 의도가 명확. JPA `boolean` 직매핑. |
| `BLOB` | `BYTEA` | Postgres 표준 바이너리 타입. |
| `JSON` | `JSONB` | 인덱싱·검색이 가능한 바이너리 JSON. Hibernate 6 native 매핑. |
| `VARCHAR(n)` | `VARCHAR(n)` | 길이 보존. |
| `ENUM('a','b')` (컬럼) | `VARCHAR(n)` + `CHECK (col IN (...))` | v1.0 호환성 우선 (§3). 별도 `CREATE TYPE … AS ENUM` 은 문서·미래용으로만 정의. |
| `DECIMAL(p,s)` | `DECIMAL(p,s)` | 그대로 사용. |
| `TEXT` | `TEXT` | 그대로 사용. |
| `DATE` | `DATE` | 그대로 사용. |
| 인덱스 `KEY ix_xxx (a, b DESC)` | `CREATE INDEX ix_xxx ON t (a, b DESC)` | DESC 정렬 보존 (PageMeta `latest` 정렬을 위해). |
| `INSERT IGNORE` | `INSERT … ON CONFLICT (...) DO NOTHING` | Postgres 등가물. seed 와 폴러 모두에 적용. |

추가 의사결정:

- `users` → `profiles` rename. PK 는 `UUID NOT NULL` 이며 `auth.users.id` 와 동일한
  값을 사용. Supabase `auth` 스키마에 대한 명시적 FK 는 두지 않는다(시스템 스키마이며
  Supabase 매니지드라 외부에서 FK 를 걸면 마이그레이션이 깨질 위험).
- `refresh_tokens` 폐기. Supabase Auth 가 refresh 토큰을 관리한다.
- 6 개 user_id FK 컬럼은 모두 `UUID`: `profiles.id`, `folders.user_id`,
  `fcm_tokens.user_id`, `search_history.user_id`, `report_read_status.user_id`,
  `notifications.user_id`.
- **컬럼 rename**: `financial_metric_catalog.code` (PK) →
  `financial_metric_catalog.metric_code` (PK) 로 변경. task-spec rename. FK 인
  `theme_metric_sensitivity.metric_code` 도 동일 이름으로 갱신됨.
  PR-07 에서 작성하는 JPA 엔티티 (`FinancialMetricCatalog`, `ThemeMetricSensitivity`)
  는 반드시 `metricCode` 필드명 + `@Column(name = "metric_code")` 를 사용해야 한다.

---

## 2. Flyway 운영 규칙

1. **V1 immutable.** `V1__init_schema.sql` 은 한 번 적용되면 절대 수정하지 않는다.
   해시가 바뀌면 다음 배포에서 `MigrationChecksumMismatch` 로 부팅이 멈춘다.
2. **보정은 V_n+1.** 컬럼 추가/삭제, 인덱스 변경, 컬럼 타입 변경 등은 새 파일
   (`V4__add_xxx.sql`, `V5__alter_yyy.sql` 등) 로 추가한다.
3. **Rollback 금지.** Flyway 의 `undo` 는 사용하지 않는다. 잘못된 마이그레이션은
   다음 forward 파일로 보정한다.
4. **Baseline 정책.** 로컬·테스트 환경은 빈 DB 에서 시작하므로
   `spring.flyway.baseline-on-migrate=true` 면 충분. 운영 환경 (Supabase) 은 최초 1
   회만 `flyway:baseline` 을 수동 실행한 뒤 이후 자동 마이그레이션.
5. **트랜잭션.** `CREATE INDEX CONCURRENTLY` 는 트랜잭션 내부에서 실행할 수 없으므로
   금지. 대용량 데이터에서 잠금이 우려되면 별도 운영 절차 (Flyway out-of-band 명령) 로
   처리하고 V_n 파일에는 일반 `CREATE INDEX` 만 쓴다.
6. **테스트.** 모든 PR 의 CI 에서 Testcontainers Postgres 로 V1 → V_n 전체 시퀀스를
   dry-run 한다 (`FlywayMigrationSmokeTest`).

---

## 3. ENUM 매핑 권장 패턴

v1.0 은 다음 hybrid 전략을 채택한다.

- 마이그레이션 파일 최상단에 `CREATE TYPE … AS ENUM` 으로 **8 개 타입 정의**:
  `platform_type`, `folder_type`, `market_type`, `period_type`, `metric_category`,
  `direction_type`, `disclosure_type`, `notification_type`.
  이 정의는 “문서” + “미래에 native ENUM 으로 migrate 할 때 쓸 자리표시자” 역할을 한다.
- **컬럼 자체는 `VARCHAR(n)` + `CHECK constraint`**.
  예: `type VARCHAR(10) NOT NULL CHECK (type IN ('default', 'custom'))`.
- JPA 매핑은 `@Enumerated(EnumType.STRING)` + `String`/`enum` 컬럼. Hibernate 6 의
  `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` 은 v1 에서 도입하지 않는다 (호환성·이식성 우선).

근거(implPlan §5.5):

- Postgres 의 native ENUM 은 값 추가시 `ALTER TYPE ADD VALUE` 가 트랜잭션 외부에서만
  가능하고, 값 삭제·재정렬은 사실상 불가능. v1.0 에서 enum 변경 비용을 낮추기 위해
  varchar+CHECK 가 안전.
- v5 단계에서 안정화되면 `ALTER TABLE … ALTER COLUMN … TYPE folder_type USING …` 로
  한 번에 native ENUM 으로 전환 가능. 그래서 `CREATE TYPE` 정의를 미리 박아둔다.

---

## 4. Hibernate / Java 매핑 cheatsheet

| 컬럼 타입 | Java 필드 | Hibernate 6 매핑 |
|---|---|---|
| `UUID` (PK) | `UUID id` | `@Id @Column(columnDefinition = "uuid") UUID id` |
| `JSONB` | `List<MetricContribution> contributions` | `@JdbcTypeCode(SqlTypes.JSON) List<MetricContribution> contributions` |
| `TIMESTAMPTZ` | `OffsetDateTime` / `Instant` | 기본 매핑 + `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` |
| `BYTEA` | `byte[]` | `@JdbcTypeCode(SqlTypes.VARBINARY) byte[] textEmbedding` |
| `VARCHAR + CHECK` | `enum FolderType` | `@Enumerated(EnumType.STRING) FolderType type` |
| `BIGINT IDENTITY` | `Long id` | `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id` |

Hibernate 6.2+ 는 JSON / UUID 를 native 로 지원하므로 `hypersistence-utils` 의존성은
필요 없다.

---

## 5. 새 마이그레이션 추가 절차 (V_n+1)

1. 파일명: `V<번호>__<snake_case_summary>.sql`. 번호는 직전 마이그레이션 +1.
   - 예: `V4__add_users_locale.sql`, `V5__alter_disclosures_add_corp_code.sql`.
2. 파일 첫 줄에 짧은 헤더 코멘트(목적, 관련 PR 번호) 를 단다.
3. 한 PR 에 새 마이그레이션이 1 개 이상이면 commit 분리를 권장 (리뷰 단위).
4. **커밋해야 할 것**: SQL 파일, 영향받는 JPA Entity, 테스트(필요 시
   `FlywayMigrationSmokeTest` 검증 강화).
5. **커밋하지 말 것**:
   - `flyway_schema_history` 행 데이터(로컬 H2 더미 등).
   - 직접 손으로 만든 production DB dump.
   - `--baseline-version` 등 운영 명령어 결과물.
6. CI 가 Testcontainers 로 V1 → V_n 전체 시퀀스를 dry-run 한다. local 에서는
   `./gradlew test --tests *FlywayMigrationSmokeTest` 로 사전 검증.

---

## 6. PR-02 seed 노트

- `V2__seed_metric_catalog.sql`: 15 개 재무지표. `ON CONFLICT (metric_code) DO
  NOTHING` 로 멱등. category 는 6 값 (`valuation`/`profitability`/`growth`/
  `solvency`/`liquidity`/`market`) 중 하나, CHECK constraint 로 보호.
- `V3__seed_stocks.sql` + `db/seed/stocks-universe.csv`: **placeholder universe**.
  `K00001`..`K00200` (200 KOSPI) + `Q00001`..`Q00150` (150 KOSDAQ) = 350 종목.
  category 는 15 개 WICS-style 버킷을 순환 (반도체, IT서비스, 바이오, …).
  - **왜 placeholder 인가?** 실시간 KRX/KIS API 호출 없이 결정론적 universe 가
    필요했고, 실제 KOSPI200/KOSDAQ150 의 안정적인 공개 ticker 리스트를 PR-02 범위
    내에서 확보하기 어려웠다. 후속 V_n 에서 실제 ticker 로 `UPDATE stocks SET
    name=…, category=…` 또는 truncate-replace 마이그레이션으로 교체 예정.
  - CSV 파일은 SQL 의 "source of truth" 로 함께 커밋되며, 추후 생성 스크립트의
    입력으로도 사용할 수 있다.

---

## 7. 참조

- `docs/erdcloud_schema.sql`: MySQL 원본 DDL.
- `docs/schema.dbml`: dbdiagram ERD (인덱스, FK ON DELETE 정책 원본).
- `docs/implPlan.md` §3 PR-02, §5.5 Gotchas.
- Flyway Postgres docs: <https://documentation.red-gate.com/fd/postgresql-184127604.html>
- Hibernate 6 ORM 사용자 가이드(JSON/UUID): <https://docs.jboss.org/hibernate/orm/6.5/userguide/html_single/Hibernate_User_Guide.html>
