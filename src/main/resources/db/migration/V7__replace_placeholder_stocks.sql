-- V3 seed는 K00001~K00350 placeholder. 실제 KRX ticker로 교체하기 위해 종속 데이터부터 정리.
-- StocksBootstrap이 부팅 시 DART corpCode.xml에서 받은 ~4000개 상장 종목을 새로 적재한다.
DELETE FROM notifications WHERE ticker LIKE 'K%';
DELETE FROM stock_themes WHERE ticker LIKE 'K%';
DELETE FROM analysis_reports WHERE ticker LIKE 'K%';
DELETE FROM favorites WHERE ticker LIKE 'K%';
DELETE FROM financial_statements WHERE ticker LIKE 'K%';
DELETE FROM stock_news WHERE ticker LIKE 'K%';
DELETE FROM disclosures WHERE ticker LIKE 'K%';
DELETE FROM stock_view_counts WHERE ticker LIKE 'K%';
DELETE FROM stocks WHERE ticker LIKE 'K%';
