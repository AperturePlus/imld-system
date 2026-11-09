package xenosoft.imldintelligence.module.careplan.internal.repository;

import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 预警事件仓储接口，负责在租户边界内持久化护理计划相关预警数据。
 */
public interface AlertEventRepository {
    /**
     * 按租户和预警事件主键查询预警事件。
     *
     * @param tenantId 租户标识
     * @param id 预警事件主键
     * @return 匹配的预警事件，不存在时返回空
     */
    Optional<AlertEvent> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部预警事件。
     *
     * @param tenantId 租户标识
     * @return 符合条件的预警事件列表
     */
    List<AlertEvent> listByTenantId(Long tenantId);

    /**
     * 按租户和护理计划主键查询预警事件列表。
     *
     * @param tenantId 租户标识
     * @param carePlanId 护理计划主键
     * @return 符合条件的预警事件列表
     */
    List<AlertEvent> listByCarePlanId(Long tenantId, Long carePlanId);

    /**
     * 按租户和患者主键查询预警事件列表。
     *
     * @param tenantId 租户标识
     * @param patientId 患者主键
     * @return 符合条件的预警事件列表
     */
    List<AlertEvent> listByPatientId(Long tenantId, Long patientId);

    /**
     * 按诊断会话和诊断结果查询已生成的告警。
     */
    Optional<AlertEvent> findBySourceSessionId(Long tenantId, Long sessionId, Long resultId);

    /**
     * 新增预警事件。
     *
     * @param alertEvent 待保存的预警事件
     * @return 保存后的预警事件
     */
    AlertEvent save(AlertEvent alertEvent);

    /**
     * 按租户、诊断会话和诊断结果幂等新增告警。
     */
    boolean saveIfAbsent(AlertEvent alertEvent);

    /**
     * 更新预警事件。
     *
     * @param alertEvent 待更新的预警事件
     * @return 更新后的预警事件
     */
    AlertEvent update(AlertEvent alertEvent);

    /**
     * 仅当当前状态匹配 expectedStatus 时推进状态，避免并发重复迁移。
     *
     * @param tenantId 租户标识
     * @param id 预警事件主键
     * @param expectedStatus 期望当前状态
     * @param targetStatus 目标状态
     * @param resolvedAt 解决时间
     * @param resolutionNote 解决说明
     * @param assignedTo 指派人
     * @return 状态迁移成功时返回 {@code true}，否则返回 {@code false}
     */
    boolean updateStatusIfCurrent(Long tenantId,
                                  Long id,
                                  String expectedStatus,
                                  String targetStatus,
                                  OffsetDateTime resolvedAt,
                                  String resolutionNote,
                                  Long assignedTo);

    /**
     * 关联随访计划并更新处理人。
     */
    boolean linkCarePlan(Long tenantId, Long id, Long carePlanId, Long assignedTo);

    /**
     * 按租户和预警事件主键删除预警事件。
     *
     * @param tenantId 租户标识
     * @param id 预警事件主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
