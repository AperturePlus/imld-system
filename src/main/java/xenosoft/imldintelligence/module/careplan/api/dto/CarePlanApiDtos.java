package xenosoft.imldintelligence.module.careplan.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 随访计划模块 DTO 分类目录。
 *
 * <p>契约围绕计划、任务、患者上报、告警与处置闭环设计，便于后续接入本地规则与云协同能力。</p>
 */
public final class CarePlanApiDtos {
    private CarePlanApiDtos() {
    }

    /**
     * 查询类 DTO。
     */
    public static final class Query {
        private Query() {
        }

        /**
         * 随访计划检索条件。
         */
        public record CarePlanPageQuery(
                @Positive(message = "patientId must be positive")
                Long patientId,
                String diseaseCode,
                String planType,
                String status,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate startFrom,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate startTo
        ) {
        }

        /**
         * 随访任务检索条件。
         */
        public record FollowupTaskPageQuery(
                @Positive(message = "carePlanId must be positive")
                Long carePlanId,
                @Positive(message = "patientId must be positive")
                Long patientId,
                String taskType,
                String status,
                @Positive(message = "assignedTo must be positive")
                Long assignedTo,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime scheduledFrom,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime scheduledTo
        ) {
        }

        /**
         * 告警事件检索条件。
         */
        public record AlertEventPageQuery(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @Positive(message = "carePlanId must be positive")
                Long carePlanId,
                String severity,
                String status,
                @Positive(message = "assignedTo must be positive")
                Long assignedTo
        ) {
        }

        /**
         * 患者上报数据检索条件。
         */
        public record PatientReportedDataPageQuery(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @Positive(message = "carePlanId must be positive")
                Long carePlanId,
                String indicatorCode,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime recordedFrom,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime recordedTo
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
         * 创建随访计划。
         */
        public record CreateCarePlanRequest(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @NotBlank(message = "diseaseCode must not be blank")
                @Size(max = 64, message = "diseaseCode must be at most 64 characters")
                String diseaseCode,
                @NotBlank(message = "planType must not be blank")
                @Size(max = 64, message = "planType must be at most 64 characters")
                String planType,
                String enrollmentType,
                @Positive(message = "templateId must be positive")
                Long templateId,
                LocalDate startDate,
                LocalDate endDate,
                @Positive(message = "createdBy must be positive")
                Long createdBy
        ) {
        }

        /**
         * 调度随访任务。
         */
        public record ScheduleFollowupTaskRequest(
                @Positive(message = "carePlanId must be positive")
                Long carePlanId,
                @Positive(message = "patientId must be positive")
                Long patientId,
                @NotBlank(message = "taskType must not be blank")
                @Size(max = 64, message = "taskType must be at most 64 characters")
                String taskType,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime scheduledAt,
                String channel,
                @Positive(message = "assignedTo must be positive")
                Long assignedTo
        ) {
        }

        /**
         * 记录患者上报数据。
         */
        public record ReportPatientDataRequest(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @Positive(message = "carePlanId must be positive")
                Long carePlanId,
                @NotBlank(message = "indicatorCode must not be blank")
                @Size(max = 64, message = "indicatorCode must be at most 64 characters")
                String indicatorCode,
                Double valueNumeric,
                String valueText,
                String source,
                JsonNode deviceInfo,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime recordedAt
        ) {
        }

        /**
         * 告警处置请求。
         */
        public record ResolveAlertRequest(
                @Positive(message = "alertId must be positive")
                Long alertId,
                @NotBlank(message = "actionType must not be blank")
                @Size(max = 64, message = "actionType must be at most 64 characters")
                String actionType,
                @Positive(message = "actionBy must be positive")
                Long actionBy,
                @Size(max = 500, message = "actionNote must be at most 500 characters")
                String actionNote,
                @Size(max = 500, message = "resolutionNote must be at most 500 characters")
                String resolutionNote
        ) {
        }

        /**
         * 从高危预警直接建立随访计划和首个随访任务。
         */
        public record CreateFollowupFromAlertRequest(
                @Size(max = 64, message = "diseaseCode must be at most 64 characters")
                String diseaseCode,
                @Size(max = 64, message = "planType must be at most 64 characters")
                String planType,
                LocalDate startDate,
                LocalDate endDate,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime scheduledAt,
                @Size(max = 32, message = "taskType must be at most 32 characters")
                String taskType,
                @Size(max = 32, message = "channel must be at most 32 characters")
                String channel,
                @Size(max = 500, message = "note must be at most 500 characters")
                String note
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
         * 随访计划响应。
         */
        public record CarePlanResponse(
                Long id,
                Long patientId,
                String diseaseCode,
                String planType,
                String enrollmentType,
                Long templateId,
                String status,
                LocalDate startDate,
                LocalDate endDate,
                Long createdBy,
                OffsetDateTime createdAt
        ) {
        }

        /**
         * 随访任务响应。
         */
        public record FollowupTaskResponse(
                Long id,
                Long carePlanId,
                Long patientId,
                String taskType,
                OffsetDateTime scheduledAt,
                String channel,
                String status,
                Long assignedTo,
                OffsetDateTime completedAt,
                String resultSummary,
                OffsetDateTime createdAt
        ) {
        }

        /**
         * 告警事件响应。
         */
        public record AlertEventResponse(
                Long id,
                Long patientId,
                String patientNo,
                String patientName,
                Long carePlanId,
                Long sourceSessionId,
                Long sourceResultId,
                String triggerType,
                JsonNode triggerDetail,
                String severity,
                String status,
                Long assignedTo,
                OffsetDateTime createdAt,
                OffsetDateTime resolvedAt,
                String resolutionNote,
                List<Shared.AlertActionItem> actions
        ) {
        }

        /**
         * 从预警建立随访后的响应。
         */
        public record CreateFollowupFromAlertResponse(
                CarePlanResponse carePlan,
                FollowupTaskResponse task,
                AlertEventResponse alert
        ) {
        }

        /**
         * 患者上报数据响应。
         */
        public record PatientReportedDataResponse(
                Long id,
                Long patientId,
                Long carePlanId,
                String indicatorCode,
                Double valueNumeric,
                String valueText,
                String source,
                JsonNode deviceInfo,
                OffsetDateTime recordedAt,
                OffsetDateTime createdAt
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
         * 告警处置动作片段。
         */
        public record AlertActionItem(
                Long id,
                Long alertId,
                String actionType,
                Long actionBy,
                String actionNote,
                OffsetDateTime createdAt
        ) {
        }
    }
}
