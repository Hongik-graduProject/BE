-- look-ahead bias 차단: 재무 수치가 실제로 공시된 날 (DART rcept_no 앞 8 자리).
-- 기존 행은 NULL 로 남고, FinancialStatementFetchService 가 다음 조회 때 채운다.
ALTER TABLE financial_statements ADD COLUMN disclosed_at DATE NULL;
