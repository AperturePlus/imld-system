package xenosoft.imldintelligence.module.diagnoses.internal.repository;

import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.query.ModelRegistryQuery;

import java.util.List;
import java.util.Optional;

/**
 * 模型注册仓储接口，负责在租户边界内维护可用模型的注册信息。
 */
public interface ModelRegistryRepository {
    /**
     * 按租户和模型注册信息主键查询模型注册信息。
     *
     * @param tenantId 租户标识
     * @param id 模型注册信息主键
     * @return 匹配的模型注册信息，不存在时返回空
     */
    Optional<ModelRegistry> findById(Long tenantId, Long id);

    /**
     * 按租户和模型编码和模型版本查询模型注册信息。
     *
     * @param tenantId 租户标识
     * @param modelCode 模型编码
     * @param modelVersion 模型版本
     * @return 匹配的模型注册信息，不存在时返回空
     */
    Optional<ModelRegistry> findByModelCodeAndModelVersion(Long tenantId, String modelCode, String modelVersion);

    /**
     * 查询租户下全部模型注册信息。
     *
     * @param tenantId 租户标识
     * @return 符合条件的模型注册信息列表
     */
    List<ModelRegistry> listByTenantId(Long tenantId);

    /**
     * 按查询条件分页查询模型注册信息，过滤/排序/分页均在 SQL 层完成。
     *
     * <p>排序规则与 {@code listModels} 一致：按 created_at 倒序。</p>
     *
     * @param query 筛选条件
     * @param offset 偏移量（从 0 开始）
     * @param limit 每页条数
     * @return 当前页的模型注册信息列表
     */
    List<ModelRegistry> query(ModelRegistryQuery query, long offset, int limit);

    /**
     * 按查询条件统计匹配的模型注册信息总数。
     *
     * @param query 筛选条件
     * @return 匹配的记录数
     */
    long count(ModelRegistryQuery query);

    /**
     * 新增模型注册信息。
     *
     * @param modelRegistry 待保存的模型注册信息
     * @return 保存后的模型注册信息
     */
    ModelRegistry save(ModelRegistry modelRegistry);

    /**
     * 并发安全地保存模型注册信息，记录已存在时不抛出唯一约束异常。
     *
     * @param modelRegistry 待保存的模型注册信息
     * @return 实际新增记录时返回 {@code true}
     */
    boolean saveIfAbsent(ModelRegistry modelRegistry);

    /**
     * 更新模型注册信息。
     *
     * @param modelRegistry 待更新的模型注册信息
     * @return 更新后的模型注册信息
     */
    ModelRegistry update(ModelRegistry modelRegistry);

    /**
     * 按租户和模型注册信息主键删除模型注册信息。
     *
     * @param tenantId 租户标识
     * @param id 模型注册信息主键
     * @return 删除成功时返回 {@code true}，否则返回 {@code false}
     */
    Boolean deleteById(Long tenantId, Long id);
}
