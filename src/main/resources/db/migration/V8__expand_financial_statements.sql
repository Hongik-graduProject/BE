-- 재무 탭 디자인 일치를 위해 financial_statements에 손익·재무·현금흐름 라인을 확장.
-- sj_div: CIS (포괄손익계산서·손익계산서), BS (재무상태표), CF (현금흐름표)
-- period 형식은 'YYYY' (연간) 또는 'YYYYQn' (분기).
ALTER TABLE financial_statements ADD COLUMN sj_div VARCHAR(4) NOT NULL DEFAULT 'CIS';
ALTER TABLE financial_statements ADD COLUMN fs_div VARCHAR(4);

-- 손익계산서 추가 컬럼
ALTER TABLE financial_statements ADD COLUMN sga BIGINT;                            -- 판매비와관리비
ALTER TABLE financial_statements ADD COLUMN non_operating_income BIGINT;           -- 영업외수익
ALTER TABLE financial_statements ADD COLUMN non_operating_expense BIGINT;          -- 영업외비용
ALTER TABLE financial_statements ADD COLUMN pretax_income BIGINT;                  -- 세전이익
ALTER TABLE financial_statements ADD COLUMN tax_expense BIGINT;                    -- 법인세비용
ALTER TABLE financial_statements ADD COLUMN controlling_net_income BIGINT;         -- 지배주주순이익
ALTER TABLE financial_statements ADD COLUMN noncontrolling_net_income BIGINT;      -- 비지배주주순이익

-- 재무상태표 추가 컬럼
ALTER TABLE financial_statements ADD COLUMN current_assets BIGINT;
ALTER TABLE financial_statements ADD COLUMN non_current_assets BIGINT;
ALTER TABLE financial_statements ADD COLUMN current_liabilities BIGINT;
ALTER TABLE financial_statements ADD COLUMN non_current_liabilities BIGINT;
ALTER TABLE financial_statements ADD COLUMN capital_stock BIGINT;
ALTER TABLE financial_statements ADD COLUMN retained_earnings BIGINT;

-- 현금흐름표 컬럼
ALTER TABLE financial_statements ADD COLUMN operating_cash_flow BIGINT;
ALTER TABLE financial_statements ADD COLUMN investing_cash_flow BIGINT;
ALTER TABLE financial_statements ADD COLUMN financing_cash_flow BIGINT;
ALTER TABLE financial_statements ADD COLUMN ending_cash BIGINT;

-- 비율 지표 (네이버 크롤링 또는 계산)
ALTER TABLE financial_statements ADD COLUMN roe DECIMAL(7,4);
ALTER TABLE financial_statements ADD COLUMN roa DECIMAL(7,4);

-- 새 (sj_div 포함) UNIQUE 인덱스로 마이그레이션하려면 기존 행이 (ticker, period_type, period) 단위로 한 행씩만
-- 있어야 하지만, V7 이전 형식 데이터는 sj_div가 없음. 그래서 기존 행은 일괄 정리하고
-- FinancialStatementFetchService가 다음 호출 때 DART에서 다시 받아 적재한다.
DELETE FROM financial_statements;

DROP INDEX IF EXISTS uq_financial_statements_ticker_period_type_period;
CREATE UNIQUE INDEX uq_financial_statements_ticker_period_type_period_sj
  ON financial_statements (ticker, period_type, period, sj_div);
