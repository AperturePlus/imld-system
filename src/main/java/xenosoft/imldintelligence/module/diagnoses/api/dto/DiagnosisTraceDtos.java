package xenosoft.imldintelligence.module.diagnoses.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 诊断决策链追溯 DTO。
 *
 * <p>把一次诊断的会话、模型、结果、医生反馈、模型调用日志和审计日志
 * 聚合到一个响应中，供合规审计和问题追溯使用。</p>
 */
public final class DiagnosisTraceDtos {

    private DiagnosisTraceDtos() {
    }

    public static final class Response {

        private Response() {
        }

        public record DiagnosisTraceResponse(
                SessionTrace session,
                ModelTrace model,
                List<ResultTrace> results,
                List<FeedbackTrace> feedbacks,
                List<ModelInvocationTrace> modelInvocations,
                List<AuditLogTrace> auditLogs
        ) {
        }

        public record SessionTrace(
                Long id,
                Long patientId,
                Long encounterId,
                Long doctorId,
                String status,
                String triggeredBy,
                Long modelRegistryId,
                String traceId,
                OffsetDateTime startedAt,
                OffsetDateTime completedAt,
                JsonNode inputSnapshot
        ) {
        }

        public record ModelTrace(
                Long id,
                String modelCode,
                String modelName,
                String modelVersion,
                String provider,
                String status
        ) {
        }

        public record ResultTrace(
                Long id,
                String diseaseCode,
                String diseaseName,
                Double confidence,
                Integer rankNo,
                String riskLevel,
                JsonNode evidenceJson,
                OffsetDateTime createdAt
        ) {
        }

        public record FeedbackTrace(
                Long id,
                Long resultId,
                Long doctorId,
                String action,
                JsonNode modifiedValue,
                String rejectReason,
                OffsetDateTime createdAt
        ) {
        }

        public record ModelInvocationTrace(
                Long id,
                String provider,
                String requestId,
                String inputDigest,
                String outputDigest,
                Integer latencyMs,
                String status,
                String errorMessage,
                OffsetDateTime createdAt
        ) {
        }

        public record AuditLogTrace(
                Long id,
                Long userId,
                String userRole,
                String action,
                String resourceType,
                String resourceId,
                String traceId,
                OffsetDateTime createdAt
        ) {
        }
    }
}
