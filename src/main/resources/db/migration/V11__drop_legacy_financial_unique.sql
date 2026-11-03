-- V8 은 sj_div 를 넣은 새 UNIQUE 인덱스를 만들면서 옛 키를 지우려 했지만, 이름을 잘못 적어
-- (uq_financial_statements_ticker_period_type_period) V1 의 uk_financial_ticker_period 가 그대로 남았다.
-- 그래서 같은 기간의 BS·CF 행을 CIS 행 옆에 넣을 수 없었다. 옛 제약을 지운다.
-- 새 키 uq_financial_statements_ticker_period_type_period_sj (ticker, period_type, period, sj_div) 는 V8 에 있다.
ALTER TABLE financial_statements DROP CONSTRAINT IF EXISTS uk_financial_ticker_period;

-- 이전 파서는 BS·CF·SCE 를 CIS 한 행에 섞어 저장했다 (sj_div 대신 없는 fs_div 를 읽음). 그 행이 남아 있으면
-- FinancialStatementFetchService 가 "이미 있음" 으로 보고 다시 받지 않는다. V8 처럼 비우고 다음 조회 때 다시 받는다.
DELETE FROM financial_statements;
