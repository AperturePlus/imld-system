package xenosoft.imldintelligence.module.diagnoses.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DoctorFeedback;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DoctorFeedbackRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.mybatis.DoctorFeedbackMapper;

import java.util.List;
import java.util.Optional;

/**
 * 医生反馈仓储实现类，基于 MyBatis-Plus 完成医生反馈的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class DoctorFeedbackRepositoryImpl implements DoctorFeedbackRepository {
    private final DoctorFeedbackMapper doctorFeedbackMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<DoctorFeedback> findById(Long tenantId, Long id) {
        return Optional.ofNullable(doctorFeedbackMapper.selectOne(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .eq(DoctorFeedback::getId, id)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DoctorFeedback> listByTenantId(Long tenantId) {
        return doctorFeedbackMapper.selectList(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .orderByDesc(DoctorFeedback::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DoctorFeedback> listBySessionId(Long tenantId, Long sessionId) {
        return doctorFeedbackMapper.selectList(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .eq(DoctorFeedback::getSessionId, sessionId)
                .orderByDesc(DoctorFeedback::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DoctorFeedback> listByResultId(Long tenantId, Long resultId) {
        return doctorFeedbackMapper.selectList(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .eq(DoctorFeedback::getResultId, resultId)
                .orderByDesc(DoctorFeedback::getId));
    }

    @Override
    public List<DoctorFeedback> listBySessionIds(Long tenantId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return doctorFeedbackMapper.selectList(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .in(DoctorFeedback::getSessionId, sessionIds)
                .orderByDesc(DoctorFeedback::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoctorFeedback save(DoctorFeedback doctorFeedback) {
        doctorFeedbackMapper.insert(doctorFeedback);
        return doctorFeedback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DoctorFeedback update(DoctorFeedback doctorFeedback) {
        doctorFeedbackMapper.update(doctorFeedback, new LambdaUpdateWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, doctorFeedback.getTenantId())
                .eq(DoctorFeedback::getId, doctorFeedback.getId()));
        return doctorFeedback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean deleteById(Long tenantId, Long id) {
        return doctorFeedbackMapper.delete(new LambdaQueryWrapper<DoctorFeedback>()
                .eq(DoctorFeedback::getTenantId, tenantId)
                .eq(DoctorFeedback::getId, id)) > 0;
    }
}
