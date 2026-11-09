-- 关联高危诊断结果与预警事件，保证同一诊断结果只生成一次预警。
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS source_session_id BIGINT;
ALTER TABLE alert_event ADD COLUMN IF NOT EXISTS source_result_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_event_source_result
    ON alert_event (tenant_id, source_session_id, source_result_id);

CREATE INDEX IF NOT EXISTS idx_alert_event_open_time
    ON alert_event (tenant_id, status, created_at DESC, id DESC);
