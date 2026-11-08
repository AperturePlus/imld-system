package xenosoft.imldintelligence.module.diagnoses.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.mybatis.DiagnosisResultMapper;

import java.util.List;
import java.util.Optional;

/**
 * 诊断结果仓储实现类，基于 MyBatis-Plus 完成诊断结果的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class DiagnosisResultRepositoryImpl implements DiagnosisResultRepository {
    private final DiagnosisResultMapper diagnosisResultMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<DiagnosisResult> findById(Long tenantId, Long id) {
        return Optional.ofNullable(diagnosisResultMapper.selectOne(new LambdaQueryWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, tenantId)
                .eq(DiagnosisResult::getId, id)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiagnosisResult> listByTenantId(Long tenantId) {
        return diagnosisResultMapper.selectList(new LambdaQueryWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, tenantId)
                .orderByDesc(DiagnosisResult::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiagnosisResult> listBySessionId(Long tenantId, Long sessionId) {
        return diagnosisResultMapper.selectList(new LambdaQueryWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, tenantId)
                .eq(DiagnosisResult::getSessionId, sessionId)
                .orderByAsc(DiagnosisResult::getRankNo)
                .orderByDesc(DiagnosisResult::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DiagnosisResult> listBySessionIds(Long tenantId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return diagnosisResultMapper.selectList(new LambdaQueryWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, tenantId)
                .in(DiagnosisResult::getSessionId, sessionIds)
                .orderByAsc(DiagnosisResult::getRankNo)
                .orderByDesc(DiagnosisResult::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DiagnosisResult save(DiagnosisResult diagnosisResult) {
        diagnosisResultMapper.insert(diagnosisResult);
        return diagnosisResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DiagnosisResult update(DiagnosisResult diagnosisResult) {
        diagnosisResultMapper.update(diagnosisResult, new LambdaUpdateWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, diagnosisResult.getTenantId())
                .eq(DiagnosisResult::getId, diagnosisResult.getId()));
        return diagnosisResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean deleteById(Long tenantId, Long id) {
        return diagnosisResultMapper.delete(new LambdaQueryWrapper<DiagnosisResult>()
                .eq(DiagnosisResult::getTenantId, tenantId)
                .eq(DiagnosisResult::getId, id)) > 0;
    }
}
