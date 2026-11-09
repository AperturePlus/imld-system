package xenosoft.imldintelligence.module.careplan.internal.repository.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;

/**
 * 预警事件 MyBatis-Plus Mapper，复用 BaseMapper 减少重复 CRUD SQL。
 */
@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    /**
     * 按诊断来源幂等插入预警记录。
     */
    @Insert("""
            INSERT INTO alert_event (
                tenant_id, patient_id, care_plan_id,
                source_session_id, source_result_id,
                trigger_type, trigger_detail, severity, status, assigned_to
            ) VALUES (
                #{event.tenantId}, #{event.patientId}, #{event.carePlanId},
                #{event.sourceSessionId}, #{event.sourceResultId},
                #{event.triggerType},
                #{event.triggerDetail,typeHandler=xenosoft.imldintelligence.common.mybatis.JsonNodeTypeHandler},
                #{event.severity}, #{event.status}, #{event.assignedTo}
            )
            ON CONFLICT (tenant_id, source_session_id, source_result_id)
            DO NOTHING
            """)
    int insertIfAbsent(@Param("event") AlertEvent event);
}
