-- 补齐病历详情所需的患者扩展字段。
ALTER TABLE patient ADD COLUMN IF NOT EXISTS occupation VARCHAR(100);
ALTER TABLE patient ADD COLUMN IF NOT EXISTS current_address VARCHAR(500);
ALTER TABLE patient ADD COLUMN IF NOT EXISTS native_place VARCHAR(200);
ALTER TABLE patient ADD COLUMN IF NOT EXISTS consanguinity BOOLEAN;

COMMENT ON COLUMN patient.occupation IS '职业。';
COMMENT ON COLUMN patient.current_address IS '现住址。';
COMMENT ON COLUMN patient.native_place IS '籍贯。';
COMMENT ON COLUMN patient.consanguinity IS '是否存在近亲婚配。';

-- 病历详情、批量会话查询和最新病史查询使用的索引。
CREATE INDEX IF NOT EXISTS idx_clinical_history_tenant_encounter_type_time
    ON clinical_history_entry (tenant_id, encounter_id, history_type, recorded_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_lab_result_tenant_encounter_time
    ON lab_result (tenant_id, encounter_id, collected_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_diag_recommend_tenant_session_priority
    ON diagnosis_recommendation (tenant_id, session_id, priority, id);

CREATE INDEX IF NOT EXISTS idx_doctor_feedback_tenant_session_time
    ON doctor_feedback (tenant_id, session_id, created_at DESC, id DESC);
