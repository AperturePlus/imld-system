package xenosoft.imldintelligence.module.diagnoses.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.ModelRegistryRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.mybatis.ModelRegistryMapper;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.query.ModelRegistryQuery;

import java.util.List;
import java.util.Optional;

/**
 * 模型注册仓储实现类，基于 MyBatis-Plus 完成模型注册的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class ModelRegistryRepositoryImpl implements ModelRegistryRepository {
    private final ModelRegistryMapper modelRegistryMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ModelRegistry> findById(Long tenantId, Long id) {
        return Optional.ofNullable(modelRegistryMapper.selectOne(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, tenantId)
                .eq(ModelRegistry::getId, id)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ModelRegistry> findByModelCodeAndModelVersion(Long tenantId, String modelCode, String modelVersion) {
        return Optional.ofNullable(modelRegistryMapper.selectOne(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, tenantId)
                .eq(ModelRegistry::getModelCode, modelCode)
                .eq(ModelRegistry::getModelVersion, modelVersion)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ModelRegistry> listByTenantId(Long tenantId) {
        return modelRegistryMapper.selectList(new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, tenantId)
                .orderByDesc(ModelRegistry::getId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ModelRegistry> query(ModelRegistryQuery query, long offset, int limit) {
        return modelRegistryMapper.selectList(buildQueryWrapper(query)
                .last("ORDER BY created_at DESC NULLS LAST"
                        + " LIMIT " + Math.max(1, limit)
                        + " OFFSET " + Math.max(0, offset)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long count(ModelRegistryQuery query) {
        return modelRegistryMapper.selectCount(buildQueryWrapper(query));
    }

    private LambdaQueryWrapper<ModelRegistry> buildQueryWrapper(ModelRegistryQuery query) {
        return new LambdaQueryWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, query.getTenantId())
                .eq(query.getProvider() != null && !query.getProvider().isEmpty(),
                        ModelRegistry::getProvider, query.getProvider())
                .eq(query.getModelType() != null && !query.getModelType().isEmpty(),
                        ModelRegistry::getModelType, query.getModelType())
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(),
                        ModelRegistry::getStatus, query.getStatus());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModelRegistry save(ModelRegistry modelRegistry) {
        modelRegistryMapper.insert(modelRegistry);
        return modelRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean saveIfAbsent(ModelRegistry modelRegistry) {
        return modelRegistryMapper.insertIfAbsent(modelRegistry) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModelRegistry update(ModelRegistry modelRegistry) {
        modelRegistryMapper.update(modelRegistry, new LambdaUpdateWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, modelRegistry.getTenantId())
                .eq(ModelRegistry::getId, modelRegistry.getId()));
        return modelRegistry;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean deleteById(Long tenantId, Long id) {
        return modelRegistryMapper.update(null, new LambdaUpdateWrapper<ModelRegistry>()
                .eq(ModelRegistry::getTenantId, tenantId)
                .eq(ModelRegistry::getId, id)
                .set(ModelRegistry::getStatus, "INACTIVE")) > 0;
    }
}
