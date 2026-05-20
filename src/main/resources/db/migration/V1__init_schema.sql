-- Flyway V1 — DO NOT MODIFY after merge. Add ALTERs as V_n+1.
-- =====================================================================
-- hongik_grad — Initial Postgres schema (v4 ThemeSense, Supabase-integrated)
-- Source: docs/erdcloud_schema.sql (MySQL DDL) + docs/schema.dbml
-- Total: 17 tables (refresh_tokens dropped — Supabase Auth manages tokens)
--        users renamed to `profiles`; PK = UUID (references auth.users.id)
--        6 user_id FK columns are UUID
--
-- Conventions (per docs/implPlan.md §5.5 Gotchas):
--   * ENUM types are CREATEd at the top for documentation / future migration.
--     Actual columns use varchar(...) + CHECK constraint (v1 strategy).
--   * BIGINT AUTO_INCREMENT  -> BIGINT GENERATED ALWAYS AS IDENTITY
--   * INT    AUTO_INCREMENT  -> INT    GENERATED ALWAYS AS IDENTITY
--   * DATETIME               -> TIMESTAMPTZ
--   * TINYINT(1)             -> BOOLEAN
--   * BLOB                   -> BYTEA
--   * JSON                   -> JSONB
--   * ON UPDATE CURRENT_TIMESTAMP removed — JPA @UpdateTimestamp replaces it.
-- =====================================================================


-- =====================================================================
-- 1) ENUM TYPE definitions (documentation only — columns use varchar + CHECK)
-- =====================================================================

CREATE TYPE platform_type     AS ENUM ('ios', 'android');
CREATE TYPE folder_type       AS ENUM ('default', 'custom');
CREATE TYPE market_type       AS ENUM ('KOSPI', 'KOSDAQ');
CREATE TYPE period_type       AS ENUM ('quarterly', 'annual');
CREATE TYPE metric_category   AS ENUM ('valuation', 'profitability', 'growth', 'solvency', 'liquidity', 'market');
CREATE TYPE direction_type    AS ENUM ('positive', 'negative', 'zero');
CREATE TYPE disclosure_type   AS ENUM ('report', 'quarterly', 'interim', 'event');
CREATE TYPE notification_type AS ENUM ('disclosure_report');


-- =====================================================================
-- 2) Tables (FK-safe order: parents before children)
-- =====================================================================

-- ---------------------------------------------------------------------
-- [USER] profiles — Supabase auth.users.id 와 1:1 매핑
--   PK는 Supabase가 발급한 UUID. auth.users 에 대한 FK 는 Supabase 시스템
--   스키마이므로 명시하지 않고 애플리케이션 무결성으로 관리한다.
-- ---------------------------------------------------------------------
CREATE TABLE profiles (
    id          UUID         NOT NULL,
    nickname    VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_profiles PRIMARY KEY (id)
);

COMMENT ON TABLE  profiles      IS 'Supabase auth.users.id 와 매핑되는 사용자 프로파일';
COMMENT ON COLUMN profiles.id   IS 'Supabase auth.users.id (UUID, 외부 시스템 발급)';


-- ---------------------------------------------------------------------
-- [USER] fcm_tokens — 디바이스별 FCM 토큰
-- ---------------------------------------------------------------------
CREATE TABLE fcm_tokens (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id       UUID         NOT NULL,
    device_token  VARCHAR(512) NOT NULL,
    platform      VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_fcm_tokens                PRIMARY KEY (id),
    CONSTRAINT uk_fcm_tokens_user_device    UNIQUE (user_id, device_token),
    CONSTRAINT ck_fcm_tokens_platform       CHECK (platform IN ('ios', 'android')),
    CONSTRAINT fk_fcm_tokens_user
        FOREIGN KEY (user_id) REFERENCES profiles (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------
-- [FOLDER] folders — 관심 폴더
-- ---------------------------------------------------------------------
CREATE TABLE folders (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id        UUID         NOT NULL,
    name           VARCHAR(30)  NOT NULL,
    color          VARCHAR(7)   NOT NULL DEFAULT '#4A90D9',
    type           VARCHAR(10)  NOT NULL DEFAULT 'custom',
    display_order  INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_folders                PRIMARY KEY (id),
    CONSTRAINT uk_folders_user_name      UNIQUE (user_id, name),
    CONSTRAINT ck_folders_type           CHECK (type IN ('default', 'custom')),
    CONSTRAINT fk_folders_user
        FOREIGN KEY (user_id) REFERENCES profiles (id) ON DELETE CASCADE
);

CREATE INDEX ix_folders_user_order ON folders (user_id, display_order);


-- ---------------------------------------------------------------------
-- [STOCK] stocks — 종목 마스터
-- ---------------------------------------------------------------------
CREATE TABLE stocks (
    ticker        VARCHAR(10)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    market        VARCHAR(10)  NOT NULL,
    category      VARCHAR(50)  NULL,
    description   TEXT         NULL,
    listed_at     DATE         NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stocks         PRIMARY KEY (ticker),
    CONSTRAINT ck_stocks_market  CHECK (market IN ('KOSPI', 'KOSDAQ'))
);

CREATE INDEX ix_stocks_name      ON stocks (name);
CREATE INDEX ix_stocks_category  ON stocks (category);


-- ---------------------------------------------------------------------
-- [FOLDER] favorites — 폴더 내 관심 종목
-- ---------------------------------------------------------------------
CREATE TABLE favorites (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    folder_id    BIGINT       NOT NULL,
    ticker       VARCHAR(10)  NOT NULL,
    memo         VARCHAR(100) NULL,
    avg_price    BIGINT       NULL,
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_favorites               PRIMARY KEY (id),
    CONSTRAINT uk_favorites_folder_ticker UNIQUE (folder_id, ticker),
    CONSTRAINT fk_favorites_folder
        FOREIGN KEY (folder_id) REFERENCES folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE
);

CREATE INDEX ix_favorites_ticker ON favorites (ticker);


-- ---------------------------------------------------------------------
-- [STOCK] search_history — 사용자별 최근 검색어
-- ---------------------------------------------------------------------
CREATE TABLE search_history (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id       UUID         NOT NULL,
    keyword       VARCHAR(100) NOT NULL,
    searched_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_search_history                   PRIMARY KEY (id),
    CONSTRAINT uk_search_history_user_keyword      UNIQUE (user_id, keyword),
    CONSTRAINT fk_search_history_user
        FOREIGN KEY (user_id) REFERENCES profiles (id) ON DELETE CASCADE
);

CREATE INDEX ix_search_history_user_searched ON search_history (user_id, searched_at DESC);


-- ---------------------------------------------------------------------
-- [STOCK] stock_view_counts — 종목별 조회수 집계
-- ---------------------------------------------------------------------
CREATE TABLE stock_view_counts (
    ticker       VARCHAR(10) NOT NULL,
    view_count   BIGINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_view_counts PRIMARY KEY (ticker),
    CONSTRAINT fk_stock_view_counts_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE
);

CREATE INDEX ix_stock_view_counts_count ON stock_view_counts (view_count DESC);


-- ---------------------------------------------------------------------
-- [STOCK] stock_news — 뉴스 크롤링 캐시
-- ---------------------------------------------------------------------
CREATE TABLE stock_news (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY,
    ticker          VARCHAR(10)  NOT NULL,
    title           VARCHAR(255) NOT NULL,
    source          VARCHAR(100) NULL,
    url             VARCHAR(500) NOT NULL,
    thumbnail_url   VARCHAR(500) NULL,
    published_at    TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_news       PRIMARY KEY (id),
    CONSTRAINT uk_stock_news_url   UNIQUE (url),
    CONSTRAINT fk_stock_news_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE
);

CREATE INDEX ix_stock_news_ticker_published ON stock_news (ticker, published_at DESC);


-- ---------------------------------------------------------------------
-- [STOCK] financial_statements — 재무제표 (분기/연간)
-- ---------------------------------------------------------------------
CREATE TABLE financial_statements (
    id                  BIGINT        GENERATED ALWAYS AS IDENTITY,
    ticker              VARCHAR(10)   NOT NULL,
    period_type         VARCHAR(10)   NOT NULL,
    period              VARCHAR(10)   NOT NULL,
    revenue             BIGINT        NULL,
    cost_of_revenue     BIGINT        NULL,
    gross_profit        BIGINT        NULL,
    operating_profit    BIGINT        NULL,
    net_income          BIGINT        NULL,
    total_assets        BIGINT        NULL,
    total_liabilities   BIGINT        NULL,
    total_equity        BIGINT        NULL,
    per                 DECIMAL(10,2) NULL,
    pbr                 DECIMAL(10,2) NULL,
    eps                 BIGINT        NULL,
    dividend_yield      DECIMAL(5,2)  NULL,
    data_source         VARCHAR(50)   NULL DEFAULT 'DART',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_financial_statements           PRIMARY KEY (id),
    CONSTRAINT uk_financial_ticker_period        UNIQUE (ticker, period_type, period),
    CONSTRAINT ck_financial_period_type          CHECK (period_type IN ('quarterly', 'annual')),
    CONSTRAINT fk_financial_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------
-- [THEME] themes — 테마 카탈로그
-- ---------------------------------------------------------------------
CREATE TABLE themes (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY,
    name               VARCHAR(50)  NOT NULL,
    description        TEXT         NULL,
    source             VARCHAR(50)  NOT NULL DEFAULT 'naver',
    centroid_embedding BYTEA        NULL,
    stage1_class_idx   INT          NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_themes                PRIMARY KEY (id),
    CONSTRAINT uk_themes_name           UNIQUE (name),
    CONSTRAINT uk_themes_class_idx      UNIQUE (stage1_class_idx)
);


-- ---------------------------------------------------------------------
-- [THEME] financial_metric_catalog — 15 fixed metric master
-- ---------------------------------------------------------------------
CREATE TABLE financial_metric_catalog (
    metric_code     VARCHAR(50)  NOT NULL,
    name_ko         VARCHAR(100) NOT NULL,
    name_en         VARCHAR(100) NOT NULL,
    unit            VARCHAR(20)  NULL,
    description     TEXT         NULL,
    category        VARCHAR(20)  NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_financial_metric_catalog       PRIMARY KEY (metric_code),
    CONSTRAINT ck_financial_metric_catalog_category
        CHECK (category IN ('valuation', 'profitability', 'growth', 'solvency', 'liquidity', 'market'))
);


-- ---------------------------------------------------------------------
-- [THEME] stock_themes — 종목별 Top-3 테마 (Stage 1 multi-label)
-- ---------------------------------------------------------------------
CREATE TABLE stock_themes (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY,
    ticker          VARCHAR(10)   NOT NULL,
    theme_id        BIGINT        NOT NULL,
    probability     DECIMAL(5,4)  NOT NULL,
    rank            SMALLINT      NOT NULL,
    is_primary      BOOLEAN       NOT NULL DEFAULT FALSE,
    model_version   VARCHAR(20)   NOT NULL,
    predicted_at    TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_themes                  PRIMARY KEY (id),
    CONSTRAINT uk_stock_themes_ticker_theme     UNIQUE (ticker, theme_id),
    CONSTRAINT uk_stock_themes_ticker_rank      UNIQUE (ticker, rank),
    CONSTRAINT fk_stock_themes_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE,
    CONSTRAINT fk_stock_themes_theme
        FOREIGN KEY (theme_id) REFERENCES themes (id) ON DELETE CASCADE
);

CREATE INDEX ix_stock_themes_primary ON stock_themes (ticker, is_primary);
CREATE INDEX ix_stock_themes_theme   ON stock_themes (theme_id);


-- ---------------------------------------------------------------------
-- [THEME] theme_metric_sensitivity — 테마 × 재무지표 β계수
-- ---------------------------------------------------------------------
CREATE TABLE theme_metric_sensitivity (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY,
    theme_id        BIGINT        NOT NULL,
    metric_code     VARCHAR(50)   NOT NULL,
    beta            DECIMAL(10,6) NOT NULL,
    abs_beta        DECIMAL(10,6) NOT NULL,
    rank            SMALLINT      NULL,
    direction       VARCHAR(10)   NOT NULL,
    r_squared       DECIMAL(5,4)  NULL,
    sample_size     INT           NULL,
    model_version   VARCHAR(20)   NOT NULL,
    computed_at     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_theme_metric_sensitivity                  PRIMARY KEY (id),
    CONSTRAINT uk_tms_theme_metric_version                  UNIQUE (theme_id, metric_code, model_version),
    CONSTRAINT ck_tms_direction
        CHECK (direction IN ('positive', 'negative', 'zero')),
    CONSTRAINT fk_tms_theme
        FOREIGN KEY (theme_id) REFERENCES themes (id) ON DELETE CASCADE,
    CONSTRAINT fk_tms_metric
        FOREIGN KEY (metric_code) REFERENCES financial_metric_catalog (metric_code) ON DELETE RESTRICT
);

CREATE INDEX ix_tms_theme_rank ON theme_metric_sensitivity (theme_id, rank, model_version);
CREATE INDEX ix_tms_metric     ON theme_metric_sensitivity (metric_code);


-- ---------------------------------------------------------------------
-- [ANALYSIS] disclosures — 공시 원본 메타데이터
-- ---------------------------------------------------------------------
CREATE TABLE disclosures (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY,
    ticker            VARCHAR(10)  NOT NULL,
    dart_filing_id    VARCHAR(50)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    filed_at          TIMESTAMPTZ  NOT NULL,
    dart_url          VARCHAR(500) NULL,
    raw_text_storage  VARCHAR(500) NULL,
    text_embedding    BYTEA        NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_disclosures                       PRIMARY KEY (id),
    CONSTRAINT uk_disclosures_dart_filing_id        UNIQUE (dart_filing_id),
    CONSTRAINT ck_disclosures_type
        CHECK (type IN ('report', 'quarterly', 'interim', 'event')),
    CONSTRAINT fk_disclosures_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE
);

CREATE INDEX ix_disclosures_ticker_filed ON disclosures (ticker, filed_at DESC);
CREATE INDEX ix_disclosures_type         ON disclosures (type);


-- ---------------------------------------------------------------------
-- [ANALYSIS] analysis_reports — 분석 보고서 (ThemeSense 출력)
-- ---------------------------------------------------------------------
CREATE TABLE analysis_reports (
    id                     BIGINT       GENERATED ALWAYS AS IDENTITY,
    ticker                 VARCHAR(10)  NOT NULL,
    disclosure_id          BIGINT       NULL,
    applied_theme_id       BIGINT       NOT NULL,
    theme_routing_score    DECIMAL(5,4) NULL,
    title                  VARCHAR(255) NOT NULL,
    summary                TEXT         NULL,
    expected_car           DECIMAL(7,4) NULL,
    car_lower              DECIMAL(7,4) NULL,
    car_upper              DECIMAL(7,4) NULL,
    metric_contributions   JSONB        NULL,
    key_findings           JSONB        NULL,
    metrics                JSONB        NULL,
    stage1_model_version   VARCHAR(20)  NULL,
    stage2_model_version   VARCHAR(20)  NULL,
    generated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_analysis_reports                          PRIMARY KEY (id),
    CONSTRAINT uk_analysis_reports_ticker_disclosure        UNIQUE (ticker, disclosure_id),
    CONSTRAINT fk_analysis_reports_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE CASCADE,
    CONSTRAINT fk_analysis_reports_theme
        FOREIGN KEY (applied_theme_id) REFERENCES themes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_analysis_reports_disclosure
        FOREIGN KEY (disclosure_id) REFERENCES disclosures (id) ON DELETE SET NULL
);

CREATE INDEX ix_analysis_reports_ticker_generated ON analysis_reports (ticker, generated_at DESC);
CREATE INDEX ix_analysis_reports_theme            ON analysis_reports (applied_theme_id);


-- ---------------------------------------------------------------------
-- [ANALYSIS] report_read_status — 사용자별 보고서 읽음 상태
-- ---------------------------------------------------------------------
CREATE TABLE report_read_status (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY,
    user_id     UUID        NOT NULL,
    report_id   BIGINT      NOT NULL,
    read_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_report_read_status                PRIMARY KEY (id),
    CONSTRAINT uk_report_read_user_report           UNIQUE (user_id, report_id),
    CONSTRAINT fk_report_read_user
        FOREIGN KEY (user_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_report_read_report
        FOREIGN KEY (report_id) REFERENCES analysis_reports (id) ON DELETE CASCADE
);


-- ---------------------------------------------------------------------
-- [ALERT] notifications — 사용자별 알림 내역
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY,
    user_id      UUID         NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NULL,
    ticker       VARCHAR(10)  NULL,
    report_id    BIGINT       NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications              PRIMARY KEY (id),
    CONSTRAINT ck_notifications_type         CHECK (type IN ('disclosure_report')),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_ticker
        FOREIGN KEY (ticker) REFERENCES stocks (ticker) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_report
        FOREIGN KEY (report_id) REFERENCES analysis_reports (id) ON DELETE SET NULL
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread  ON notifications (user_id, is_read, created_at DESC);
