-- 새 공시 알림(분석 무관) 지원: 알림 타입 확장 + 공시 링크 컬럼
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_type
    CHECK (type IN ('disclosure_report', 'new_disclosure'));

ALTER TABLE notifications ADD COLUMN disclosure_id BIGINT NULL;
ALTER TABLE notifications ADD CONSTRAINT fk_notifications_disclosure
    FOREIGN KEY (disclosure_id) REFERENCES disclosures (id) ON DELETE SET NULL;
