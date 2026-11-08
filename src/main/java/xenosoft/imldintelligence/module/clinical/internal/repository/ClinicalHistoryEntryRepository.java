package xenosoft.imldintelligence.module.clinical.internal.repository;

import xenosoft.imldintelligence.module.clinical.internal.model.ClinicalHistoryEntry;

import java.util.List;
import java.util.Optional;

/**
 * 临床病史条目仓储接口，负责在租户边界内维护临床病史数据。
 */
public interface ClinicalHistoryEntryRepository {
    /**
     * 按租户和临床病史条目主键查询临床病史条目。
     *
     * @param tenantId 租户标识
     * @param id 临床病史条目主键
     * @return 匹配的临床病史条目，不存在时返回空
     */
    Optional<ClinicalHistoryEntry> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部临床病史条目。
     *
     * @param tenantId 租户标识
     * @return 符合条件的临床病史条目列表
     */
    List<ClinicalHistoryEntry> listByTenantId(Long tenantId);

    /**
     * 按租户和患者主键查询临床病史条目列表。
     *
     * @param tenantId 租户标识
     * @param patientId 患者主键
     * @return 符合条件的临床病史条目列表
     */
    List<ClinicalHistoryEntry> listByPatientId(Long tenantId, Long patientId);

    /**
     * 按租户和就诊记录主键查询临床病史条目列表。
     *
     * @param tenantId 租户标识
     * @param encounterId 就诊记录主键
     * @return 符合条件的临床病史条目列表
     */
    List<ClinicalHistoryEntry> listByEncounterId(Long tenantId, Long encounterId);

    /**
     * 按租户、就诊记录和病史类型查询最新一条病史记录。
     */
    Optional<ClinicalHistoryEntry> findLatestByEncounterIdAndType(
            Long tenantId, Long encounterId, String historyType);

    /**
     * 新增临床病史条目。
     *
     * @param clinicalHistoryEntry 待保存的临床病史条目
     * @return 保存后的临床病史条目
     */
    ClinicalHistoryEntry save(ClinicalHistoryEntry clinicalHistoryEntry);

    /**
     * 更新临床病史条目。
     *
     * @param clinicalHistoryEntry 待更新的临床病史条目
     * @return 更新后的临床病史条目
     */
    ClinicalHistoryEntry update(ClinicalHistoryEntry clinicalHistoryEntry);

    /**
     * 按租户和临床病史条目主键删除临床病史条目。
     *
     * @param tenantId 租户标识
     * @param id 临床病史条目主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
