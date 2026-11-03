-- 테마 탐색에서 종목을 시가총액 순으로 보여주려고 원 단위 시가총액을 둔다.
-- CorpCodeBootstrap 이 부팅 때 네이버 시장별 목록(marketValueRaw)으로 채우고 갱신한다.
ALTER TABLE stocks ADD COLUMN market_cap BIGINT;
CREATE INDEX idx_stocks_market_cap ON stocks (market_cap DESC NULLS LAST);
