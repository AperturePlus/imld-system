package xenosoft.imldintelligence.module.diagnoses.internal.repository;

import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisRecommendation;

import java.util.List;
import java.util.Optional;

/**
 * 诊断建议仓储接口，负责在租户边界内持久化诊断建议数据。
 */
public interface DiagnosisRecommendationRepository {
    /**
     * 按租户和诊断建议主键查询诊断建议。
     *
     * @param tenantId 租户标识
     * @param id 诊断建议主键
     * @return 匹配的诊断建议，不存在时返回空
     */
    Optional<DiagnosisRecommendation> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部诊断建议。
     *
     * @param tenantId 租户标识
     * @return 符合条件的诊断建议列表
     */
    List<DiagnosisRecommendation> listByTenantId(Long tenantId);

    /**
     * 按租户和诊断会话主键查询诊断建议列表。
     *
     * @param tenantId 租户标识
     * @param sessionId 诊断会话主键
     * @return 符合条件的诊断建议列表
     */
    List<DiagnosisRecommendation> listBySessionId(Long tenantId, Long sessionId);

    /**
     * 按租户和诊断会话主键集合批量查询诊断建议列表。
     */
    List<DiagnosisRecommendation> listBySessionIds(Long tenantId, List<Long> sessionIds);

    /**
     * 新增诊断建议。
     *
     * @param diagnosisRecommendation 待保存的诊断建议
     * @return 保存后的诊断建议
     */
    DiagnosisRecommendation save(DiagnosisRecommendation diagnosisRecommendation);

    /**
     * 批量新增诊断建议。
     *
     * @param recommendations 待保存的诊断建议列表
     * @return 实际插入的记录数
     */
    int saveBatch(List<DiagnosisRecommendation> recommendations);

    /**
     * 更新诊断建议。
     *
     * @param diagnosisRecommendation 待更新的诊断建议
     * @return 更新后的诊断建议
     */
    DiagnosisRecommendation update(DiagnosisRecommendation diagnosisRecommendation);

    /**
     * 按租户和诊断建议主键删除诊断建议。
     *
     * @param tenantId 租户标识
     * @param id 诊断建议主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
