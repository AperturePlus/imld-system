package xenosoft.imldintelligence.module.diagnoses.internal.repository;

import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.query.DiagnosisSessionQuery;

import java.util.List;
import java.util.Optional;

/**
 * 诊断会话仓储接口，负责在租户边界内持久化诊断会话数据。
 */
public interface DiagnosisSessionRepository {
    /**
     * 按租户和诊断会话主键查询诊断会话。
     *
     * @param tenantId 租户标识
     * @param id 诊断会话主键
     * @return 匹配的诊断会话，不存在时返回空
     */
    Optional<DiagnosisSession> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部诊断会话。
     *
     * @param tenantId 租户标识
     * @return 符合条件的诊断会话列表
     */
    List<DiagnosisSession> listByTenantId(Long tenantId);

    /**
     * 按租户和患者主键查询诊断会话列表。
     *
     * @param tenantId 租户标识
     * @param patientId 患者主键
     * @return 符合条件的诊断会话列表
     */
    List<DiagnosisSession> listByPatientId(Long tenantId, Long patientId);

    /**
     * 按租户和患者主键集合批量查询诊断会话列表。
     *
     * @param tenantId 租户标识
     * @param patientIds 患者主键集合
     * @return 符合条件的诊断会话列表
     */
    List<DiagnosisSession> listByPatientIds(Long tenantId, List<Long> patientIds);

    /**
     * 按租户和就诊记录主键查询诊断会话列表。
     *
     * @param tenantId 租户标识
     * @param encounterId 就诊记录主键
     * @return 符合条件的诊断会话列表
     */
    List<DiagnosisSession> listByEncounterId(Long tenantId, Long encounterId);

    /**
     * 按查询条件分页查询诊断会话，过滤/排序/分页均在 SQL 层完成。
     *
     * <p>排序规则与 {@code listSessions} 一致：按 started_at（缺失时回退 created_at）倒序，再按 id 倒序。</p>
     *
     * @param query 筛选条件
     * @param offset 偏移量（从 0 开始）
     * @param limit 每页条数
     * @return 当前页的诊断会话列表
     */
    List<DiagnosisSession> query(DiagnosisSessionQuery query, long offset, int limit);

    /**
     * 按查询条件统计匹配的诊断会话总数。
     *
     * @param query 筛选条件
     * @return 匹配的记录数
     */
    long count(DiagnosisSessionQuery query);

    /**
     * 新增诊断会话。
     *
     * @param diagnosisSession 待保存的诊断会话
     * @return 保存后的诊断会话
     */
    DiagnosisSession save(DiagnosisSession diagnosisSession);

    /**
     * 更新诊断会话。
     *
     * @param diagnosisSession 待更新的诊断会话
     * @return 更新后的诊断会话
     */
    DiagnosisSession update(DiagnosisSession diagnosisSession);

    /**
     * 按租户和诊断会话主键删除诊断会话。
     *
     * @param tenantId 租户标识
     * @param id 诊断会话主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
