-- =====================================================================
-- V2 — Seed financial_metric_catalog with 15 fixed metrics.
-- Source: docs/implPlan.md §3 PR-02 commit 2 (15 metric list + categories).
-- Idempotent: ON CONFLICT (metric_code) DO NOTHING.
-- =====================================================================

INSERT INTO financial_metric_catalog (metric_code, name_ko, name_en, unit, category, display_order) VALUES
    ('per',            '주가수익비율',     'Price Earnings Ratio',    '배',   'valuation',     1),
    ('pbr',            '주가순자산비율',   'Price to Book Ratio',     '배',   'valuation',     2),
    ('eps',            '주당순이익',       'Earnings Per Share',      '원',   'valuation',     3),
    ('roe',            '자기자본이익률',   'Return on Equity',        '%',    'profitability', 4),
    ('roa',            '총자산이익률',     'Return on Assets',        '%',    'profitability', 5),
    ('op_margin',      '영업이익률',       'Operating Margin',        '%',    'profitability', 6),
    ('net_margin',     '순이익률',         'Net Profit Margin',       '%',    'profitability', 7),
    ('sales_growth',   '매출증가율',       'Sales Growth Rate',       '%',    'growth',        8),
    ('op_growth',      '영업이익증가율',   'Operating Income Growth', '%',    'growth',        9),
    ('debt_ratio',     '부채비율',         'Debt to Equity Ratio',    '%',    'solvency',     10),
    ('current_ratio',  '유동비율',         'Current Ratio',           '%',    'liquidity',    11),
    ('foreign_ratio',  '외국인 보유 비중', 'Foreign Holding Ratio',   '%',    'market',       12),
    ('volatility',     '변동성',           'Volatility',              '%',    'market',       13),
    ('dividend_yield', '배당수익률',       'Dividend Yield',          '%',    'market',       14),
    ('market_cap',     '시가총액',         'Market Capitalization',   '억원', 'market',       15)
ON CONFLICT (metric_code) DO NOTHING;
