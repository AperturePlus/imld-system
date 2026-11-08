-- 诊断会话链路追踪字段，用于串联审计日志与模型调用日志。
ALTER TABLE diagnosis_session ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_diagnosis_session_trace
    ON diagnosis_session (tenant_id, trace_id);
