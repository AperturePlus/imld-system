package xenosoft.imldintelligence.module.screening.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 筛查模块 DTO 分类目录。
 *
 * <p>该模块处于患者入口和院内转介之间，因此契约同时覆盖问卷发布、答卷提交和转介审批。</p>
 */
public final class ScreeningApiDtos {
    private ScreeningApiDtos() {
    }

    /**
     * 查询类 DTO。
     */
    public static final class Query {
        private Query() {
        }

        /**
         * 问卷检索条件。
         */
        public record QuestionnairePageQuery(
                String diseaseScope,
                String status,
                Boolean validatedFlag,
                Integer versionNo
        ) {
        }

        /**
         * 问卷答卷检索条件。
         */
        public record QuestionnaireResponsePageQuery(
                @Positive(message = "questionnaireId must be positive")
                Long questionnaireId,
                @Positive(message = "tocUserId must be positive")
                Long tocUserId,
                String riskLevel,
                Boolean dataConsent,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime createdFrom,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime createdTo
        ) {
        }

        /**
         * 转介检索条件。
         */
        public record TransferPageQuery(
                @Positive(message = "responseId must be positive")
                Long responseId,
                @Positive(message = "patientId must be positive")
                Long patientId,
                String transferStatus,
                @Positive(message = "approvedBy must be positive")
                Long approvedBy
        ) {
        }
    }

    /**
     * 写入类 DTO。
     */
    public static final class Request {
        private Request() {
        }

        /**
         * 发布问卷。
         */
        public record PublishQuestionnaireRequest(
                @NotBlank(message = "questionnaireCode must not be blank")
                @Size(max = 64, message = "questionnaireCode must be at most 64 characters")
                String questionnaireCode,
                @NotBlank(message = "title must not be blank")
                @Size(max = 128, message = "title must be at most 128 characters")
                String title,
                String description,
                String diseaseScope,
                Integer versionNo,
                Boolean validatedFlag,
                List<Shared.QuestionItem> questions
        ) {
        }

        /**
         * 提交问卷答卷。
         */
        public record SubmitQuestionnaireResponseRequest(
                @Positive(message = "questionnaireId must be positive")
                Long questionnaireId,
                @Positive(message = "tocUserId must be positive")
                Long tocUserId,
                List<Shared.AnswerItem> answers,
                BigDecimal riskScore,
                String riskLevel,
                String suggestion,
                Boolean canShowPatient,
                Boolean dataConsent,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime expiresAt
        ) {
        }

        /**
         * 审批转介。
         */
        public record ApproveClinicalTransferRequest(
                @Positive(message = "responseId must be positive")
                Long responseId,
                @Positive(message = "patientId must be positive")
                Long patientId,
                @Positive(message = "approvedBy must be positive")
                Long approvedBy,
                @NotBlank(message = "transferStatus must not be blank")
                @Size(max = 32, message = "transferStatus must be at most 32 characters")
                String transferStatus,
                @Size(max = 500, message = "transferNote must be at most 500 characters")
                String transferNote
        ) {
        }
    }

    /**
     * 响应类 DTO。
     */
    public static final class Response {
        private Response() {
        }

        /**
         * 问卷详情响应。
         */
        public record QuestionnaireDetailResponse(
                Long id,
                String questionnaireCode,
                String title,
                String description,
                String diseaseScope,
                String status,
                Integer versionNo,
                Boolean validatedFlag,
                OffsetDateTime createdAt,
                List<Shared.QuestionItem> questions
        ) {
        }

        /**
         * 问卷答卷响应。
         */
        public record QuestionnaireSubmissionResponse(
                Long id,
                Long questionnaireId,
                Long tocUserId,
                String responseNo,
                BigDecimal riskScore,
                String riskLevel,
                String suggestion,
                Boolean canShowPatient,
                Boolean dataConsent,
                OffsetDateTime createdAt,
                OffsetDateTime expiresAt
        ) {
        }

        /**
         * 转介响应。
         */
        public record ClinicalTransferResponse(
                Long id,
                Long responseId,
                Long patientId,
                String transferStatus,
                Long approvedBy,
                OffsetDateTime approvedAt,
                String transferNote,
                OffsetDateTime createdAt
        ) {
        }

        /**
         * Web 筛查总览响应。
         */
        public record ScreeningOverviewResponse(
                String updatedAt,
                List<StatCardItem> statCards,
                List<RiskDistributionItem> riskDistribution,
                List<TopGeneItem> topGenes,
                AiEfficiencyMetrics aiEfficiency,
                List<HighRiskPatientItem> highRiskPatients
        ) {
        }

        public record StatCardItem(
                String title,
                BigDecimal value,
                String color,
                String icon,
                BigDecimal trend,
                String suffix
        ) {
        }

        public record RiskDistributionItem(
                String level,
                Integer count,
                BigDecimal percentage,
                String color
        ) {
        }

        public record TopGeneItem(
                String name,
                String desc,
                BigDecimal percentage
        ) {
        }

        public record AiEfficiencyMetrics(
                BigDecimal diagnosisMatchRate,
                String missRate,
                String avgDuration
        ) {
        }

        public record HighRiskPatientItem(
                String date,
                String name,
                Integer age,
                String clue,
                String aiSuggest,
                Long alertId,
                Long patientId,
                String patientNo,
                Long diagnosisSessionId,
                Long resultId,
                Boolean phoneAvailable
        ) {
        }
    }

    /**
     * 共享片段 DTO。
     */
    public static final class Shared {
        private Shared() {
        }

        /**
         * 问题片段。
         */
        public record QuestionItem(
                String questionNo,
                String content,
                String questionType,
                JsonNode optionsJson,
                JsonNode scoringRuleJson,
                Integer sortOrder,
                Boolean requiredFlag
        ) {
        }

        /**
         * 作答片段。
         */
        public record AnswerItem(
                String questionNo,
                JsonNode answerValue,
                String answerText
        ) {
        }
    }
}
