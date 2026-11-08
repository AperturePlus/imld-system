package xenosoft.imldintelligence.module.diagnoses.internal.repository;

import xenosoft.imldintelligence.module.diagnoses.internal.model.DoctorFeedback;

import java.util.List;
import java.util.Optional;

/**
 * 医生反馈仓储接口，负责在租户边界内维护医生复核与反馈数据。
 */
public interface DoctorFeedbackRepository {
    /**
     * 按租户和医生反馈主键查询医生反馈。
     *
     * @param tenantId 租户标识
     * @param id 医生反馈主键
     * @return 匹配的医生反馈，不存在时返回空
     */
    Optional<DoctorFeedback> findById(Long tenantId, Long id);

    /**
     * 查询租户下全部医生反馈。
     *
     * @param tenantId 租户标识
     * @return 符合条件的医生反馈列表
     */
    List<DoctorFeedback> listByTenantId(Long tenantId);

    /**
     * 按租户和诊断会话主键查询医生反馈列表。
     *
     * @param tenantId 租户标识
     * @param sessionId 诊断会话主键
     * @return 符合条件的医生反馈列表
     */
    List<DoctorFeedback> listBySessionId(Long tenantId, Long sessionId);

    /**
     * 按租户和诊断结果主键查询医生反馈列表。
     *
     * @param tenantId 租户标识
     * @param resultId 诊断结果主键
     * @return 符合条件的医生反馈列表
     */
    List<DoctorFeedback> listByResultId(Long tenantId, Long resultId);

    /**
     * 按租户和诊断会话主键集合批量查询医生反馈列表。
     *
     * @param tenantId 租户标识
     * @param sessionIds 诊断会话主键集合
     * @return 符合条件的医生反馈列表
     */
    List<DoctorFeedback> listBySessionIds(Long tenantId, List<Long> sessionIds);

    /**
     * 新增医生反馈。
     *
     * @param doctorFeedback 待保存的医生反馈
     * @return 保存后的医生反馈
     */
    DoctorFeedback save(DoctorFeedback doctorFeedback);

    /**
     * 更新医生反馈。
     *
     * @param doctorFeedback 待更新的医生反馈
     * @return 更新后的医生反馈
     */
    DoctorFeedback update(DoctorFeedback doctorFeedback);

    /**
     * 按租户和医生反馈主键删除医生反馈。
     *
     * @param tenantId 租户标识
     * @param id 医生反馈主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
