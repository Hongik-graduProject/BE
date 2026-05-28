-- PR-08 round 3: schema-entity alignment for ThemeSense ETL + Analysis
-- Adds created_at/updated_at to stock_themes and theme_metric_sensitivity (BaseTimeEntity mapping)
-- Widens model_version columns to fit "themesense-stage1-v1.0" (22 chars)
-- Widens themes.source to match entity

ALTER TABLE stock_themes
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE theme_metric_sensitivity
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE financial_metric_catalog
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE stock_themes ALTER COLUMN model_version TYPE VARCHAR(50);
ALTER TABLE theme_metric_sensitivity ALTER COLUMN model_version TYPE VARCHAR(50);
ALTER TABLE analysis_reports ALTER COLUMN stage1_model_version TYPE VARCHAR(50);
ALTER TABLE analysis_reports ALTER COLUMN stage2_model_version TYPE VARCHAR(50);

ALTER TABLE themes ALTER COLUMN source TYPE VARCHAR(100);
