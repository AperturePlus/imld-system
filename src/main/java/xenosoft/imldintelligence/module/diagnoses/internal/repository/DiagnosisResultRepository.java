package xenosoft.imldintelligence.module.diagnoses.internal.repository;

import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;

import java.util.List;
import java.util.Optional;

/**
 * 诊断结果仓储接口，负责在租户边界内持久化诊断结果数据。
 */
public interface DiagnosisResultRepository {
    /**
     * 按租户和诊断结果主键查询诊断结果。
     *
     * @param tenantId 租户标识
     * @param id 诊断结果主键
     * @return 匹配的诊断结果，不存在时返回空
     */
    Optional<DiagnosisResult> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部诊断结果。
     *
     * @param tenantId 租户标识
     * @return 符合条件的诊断结果列表
     */
    List<DiagnosisResult> listByTenantId(Long tenantId);

    /**
     * 按租户和诊断会话主键查询诊断结果列表。
     *
     * @param tenantId 租户标识
     * @param sessionId 诊断会话主键
     * @return 符合条件的诊断结果列表
     */
    List<DiagnosisResult> listBySessionId(Long tenantId, Long sessionId);

    /**
     * 按租户和诊断会话主键集合批量查询诊断结果列表。
     *
     * @param tenantId 租户标识
     * @param sessionIds 诊断会话主键集合
     * @return 符合条件的诊断结果列表
     */
    List<DiagnosisResult> listBySessionIds(Long tenantId, List<Long> sessionIds);

    /**
     * 新增诊断结果。
     *
     * @param diagnosisResult 待保存的诊断结果
     * @return 保存后的诊断结果
     */
    DiagnosisResult save(DiagnosisResult diagnosisResult);

    /**
     * 更新诊断结果。
     *
     * @param diagnosisResult 待更新的诊断结果
     * @return 更新后的诊断结果
     */
    DiagnosisResult update(DiagnosisResult diagnosisResult);

    /**
     * 按租户和诊断结果主键删除诊断结果。
     *
     * @param tenantId 租户标识
     * @param id 诊断结果主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
