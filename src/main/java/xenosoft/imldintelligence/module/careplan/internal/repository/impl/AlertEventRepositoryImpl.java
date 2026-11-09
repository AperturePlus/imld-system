package xenosoft.imldintelligence.module.careplan.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.mybatis.AlertEventMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 预警事件仓储实现类，基于 MyBatis-Plus 完成预警事件的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class AlertEventRepositoryImpl implements AlertEventRepository {
    private final AlertEventMapper alertEventMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AlertEvent> findById(Long tenantId, Long id) {
        return Optional.ofNullable(alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getId, id)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AlertEvent> listByTenantId(Long tenantId) {
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .orderByDesc(AlertEvent::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AlertEvent> listByCarePlanId(Long tenantId, Long carePlanId) {
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getCarePlanId, carePlanId)
                .orderByDesc(AlertEvent::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AlertEvent> listByPatientId(Long tenantId, Long patientId) {
        return alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getPatientId, patientId)
                .orderByDesc(AlertEvent::getId));
    }

    @Override
    public Optional<AlertEvent> findBySourceSessionId(Long tenantId, Long sessionId, Long resultId) {
        return Optional.ofNullable(alertEventMapper.selectOne(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getSourceSessionId, sessionId)
                .eq(AlertEvent::getSourceResultId, resultId)
                .last("LIMIT 1")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AlertEvent save(AlertEvent alertEvent) {
        alertEventMapper.insert(alertEvent);
        return alertEvent;
    }

    @Override
    public boolean saveIfAbsent(AlertEvent alertEvent) {
        return alertEventMapper.insertIfAbsent(alertEvent) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AlertEvent update(AlertEvent alertEvent) {
        alertEventMapper.update(alertEvent, new LambdaUpdateWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, alertEvent.getTenantId())
                .eq(AlertEvent::getId, alertEvent.getId()));
        return alertEvent;
    }

    @Override
    public boolean updateStatusIfCurrent(Long tenantId,
                                         Long id,
                                         String expectedStatus,
                                         String targetStatus,
                                         OffsetDateTime resolvedAt,
                                         String resolutionNote,
                                         Long assignedTo) {
        LambdaUpdateWrapper<AlertEvent> wrapper = new LambdaUpdateWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getId, id)
                .eq(AlertEvent::getStatus, expectedStatus)
                .set(AlertEvent::getStatus, targetStatus)
                .set(AlertEvent::getResolvedAt, resolvedAt)
                .set(AlertEvent::getResolutionNote, resolutionNote)
                .set(AlertEvent::getAssignedTo, assignedTo);
        return alertEventMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean linkCarePlan(Long tenantId, Long id, Long carePlanId, Long assignedTo) {
        return alertEventMapper.update(null, new LambdaUpdateWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getId, id)
                .set(AlertEvent::getCarePlanId, carePlanId)
                .set(AlertEvent::getAssignedTo, assignedTo)) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean deleteById(Long tenantId, Long id) {
        return alertEventMapper.delete(new LambdaQueryWrapper<AlertEvent>()
                .eq(AlertEvent::getTenantId, tenantId)
                .eq(AlertEvent::getId, id)) > 0;
    }
}
