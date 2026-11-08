package xenosoft.imldintelligence.module.diagnoses.internal.repository.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisRecommendation;

import java.util.List;

/**
 * 诊断建议 MyBatis-Plus Mapper，复用 BaseMapper 减少重复 CRUD SQL。
 */
@Mapper
public interface DiagnosisRecommendationMapper extends BaseMapper<DiagnosisRecommendation> {

    /**
     * 批量插入诊断建议。
     *
     * <p>显式指定插入列，{@code created_at} 由数据库默认值填充，
     * 避免模型未设置创建时间时插入 null 违反非空约束。</p>
     *
     * @param list 待插入的诊断建议列表
     * @return 实际插入的记录数
     */
    @Insert({
            "<script>",
            "INSERT INTO diagnosis_recommendation",
            " (tenant_id, session_id, rec_type, content, priority, reason)",
            " VALUES",
            "<foreach collection='list' item='r' separator=','>",
            " (#{r.tenantId}, #{r.sessionId}, #{r.recType}, #{r.content}, #{r.priority}, #{r.reason})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("list") List<DiagnosisRecommendation> list);
}
