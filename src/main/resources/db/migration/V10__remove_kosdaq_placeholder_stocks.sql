-- V3 seed는 KOSPI K00001~K00200 + KOSDAQ Q00001~Q00150 placeholder였는데 V7은 'K%'만 지웠다.
-- 남은 Q placeholder를 같은 순서로 정리한다. 조건은 V3 seed 형식(Q + 숫자 5자리)으로 좁혀 실제 ticker와 겹치지 않게 한다.
-- 종속 테이블 FK는 모두 ON DELETE CASCADE/SET NULL이지만 V7과 같은 방식으로 명시적으로 지운다.
DELETE FROM notifications WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM stock_themes WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM analysis_reports WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM favorites WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM financial_statements WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM stock_news WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM disclosures WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM stock_view_counts WHERE ticker ~ '^Q[0-9]{5}$';
DELETE FROM stocks WHERE ticker ~ '^Q[0-9]{5}$';
