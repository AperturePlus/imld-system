package xenosoft.imldintelligence.module.diagnoses.internal.repository.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;

/**
 * 模型注册 MyBatis-Plus Mapper，复用 BaseMapper 减少重复 CRUD SQL。
 */
@Mapper
public interface ModelRegistryMapper extends BaseMapper<ModelRegistry> {

    /**
     * 并发安全地插入模型注册记录，已存在时不产生唯一约束异常。
     */
    @Insert("""
            INSERT INTO model_registry (
                tenant_id, model_code, model_name, model_type,
                model_version, provider, status, released_at
            ) VALUES (
                #{model.tenantId}, #{model.modelCode}, #{model.modelName}, #{model.modelType},
                #{model.modelVersion}, #{model.provider}, #{model.status}, #{model.releasedAt}
            )
            ON CONFLICT (tenant_id, model_code, model_version) DO NOTHING
            """)
    int insertIfAbsent(@Param("model") ModelRegistry model);
}
