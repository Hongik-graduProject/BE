-- DART 재무 API는 corp_code(8자리)로 종목을 식별한다. ticker(6자리) → corp_code 매핑을
-- 캐시하기 위해 stocks에 컬럼을 추가하고, 부팅 시 CorpCodeBootstrap이 자동 채움.
ALTER TABLE stocks ADD COLUMN corp_code VARCHAR(8) NULL;
CREATE INDEX idx_stocks_corp_code ON stocks (corp_code);
