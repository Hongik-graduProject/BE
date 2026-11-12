-- 타임머신 판정용 일별 종가. KOSPI 지수는 ticker = 'KOSPI' 로 같이 담는다 (지수는 0.01pt 단위 정수).
-- stocks 로 FK 를 걸지 않는다: 지수 행이 있고, 상장폐지 종목의 과거 시세도 남겨야 생존 편향이 줄어든다.
CREATE TABLE daily_prices (
    ticker      VARCHAR(10) NOT NULL,
    trade_date  DATE        NOT NULL,
    close       BIGINT      NOT NULL,
    CONSTRAINT pk_daily_prices PRIMARY KEY (ticker, trade_date)
);
