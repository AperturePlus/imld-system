package xenosoft.imldintelligence.module.diagnoses.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import xenosoft.imldintelligence.common.model.AuditLog;
import xenosoft.imldintelligence.common.model.ModelInvocationLog;
import xenosoft.imldintelligence.module.audit.internal.repository.ModelInvocationLogRepository;
import xenosoft.imldintelligence.module.audit.internal.repository.query.AuditLogQuery;
import xenosoft.imldintelligence.module.audit.internal.repository.query.ModelInvocationLogQuery;
import xenosoft.imldintelligence.module.audit.internal.service.AuditQueryService;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosisTraceDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DoctorFeedback;
import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DoctorFeedbackRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.ModelRegistryRepository;

import java.util.List;

/**
 * 诊断决策链追溯服务。
 *
 * <p>按诊断会话聚合会话本身、模型、诊断结果、医生反馈、模型调用日志和审计日志，
 * 形成一条可审计的完整决策链。</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosisTraceService {

    private static final String RESOURCE_TYPE = "DIAGNOSIS_SESSION";
    private static final int MAX_TRACE_ITEMS = 200;

    private final DiagnosisSessionRepository sessionRepository;
    private final DiagnosisResultRepository resultRepository;
    private final DoctorFeedbackRepository feedbackRepository;
    private final ModelRegistryRepository modelRegistryRepository;
    private final ModelInvocationLogRepository modelInvocationLogRepository;
    private final AuditQueryService auditQueryService;

    /**
     * 查询指定诊断会话的完整决策链。
     *
     * @param tenantId 租户标识
     * @param sessionId 诊断会话主键
     * @return 聚合后的决策链
     */
    @Transactional(readOnly = true)
    public DiagnosisTraceDtos.Response.DiagnosisTraceResponse getTrace(Long tenantId, Long sessionId) {
        DiagnosisSession session = sessionRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis session not found"));

        ModelRegistry model = session.getModelRegistryId() == null ? null
                : modelRegistryRepository.findById(tenantId, session.getModelRegistryId()).orElse(null);

        List<DiagnosisResult> results = resultRepository.listBySessionId(tenantId, sessionId);
        List<DoctorFeedback> feedbacks = feedbackRepository.listBySessionId(tenantId, sessionId);

        ModelInvocationLogQuery modelQuery = new ModelInvocationLogQuery();
        modelQuery.setTenantId(tenantId);
        modelQuery.setSessionId(sessionId);
        List<ModelInvocationLog> modelInvocations = modelInvocationLogRepository.query(modelQuery, 0, MAX_TRACE_ITEMS);

        AuditLogQuery auditQuery = new AuditLogQuery();
        auditQuery.setTenantId(tenantId);
        auditQuery.setResourceType(RESOURCE_TYPE);
        auditQuery.setResourceId(String.valueOf(sessionId));
        List<AuditLog> auditLogs = auditQueryService.queryAuditLogs(auditQuery, 0, MAX_TRACE_ITEMS).items();

        return new DiagnosisTraceDtos.Response.DiagnosisTraceResponse(
                toSessionTrace(session),
                toModelTrace(model),
                results.stream().map(this::toResultTrace).toList(),
                feedbacks.stream().map(this::toFeedbackTrace).toList(),
                modelInvocations.stream().map(this::toModelInvocationTrace).toList(),
                auditLogs.stream().map(this::toAuditLogTrace).toList()
        );
    }

    private DiagnosisTraceDtos.Response.SessionTrace toSessionTrace(DiagnosisSession session) {
        return new DiagnosisTraceDtos.Response.SessionTrace(
                session.getId(),
                session.getPatientId(),
                session.getEncounterId(),
                session.getDoctorId(),
                session.getStatus(),
                session.getTriggeredBy(),
                session.getModelRegistryId(),
                session.getTraceId(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getInputSnapshot()
        );
    }

    private DiagnosisTraceDtos.Response.ModelTrace toModelTrace(ModelRegistry model) {
        if (model == null) {
            return null;
        }
        return new DiagnosisTraceDtos.Response.ModelTrace(
                model.getId(),
                model.getModelCode(),
                model.getModelName(),
                model.getModelVersion(),
                model.getProvider(),
                model.getStatus()
        );
    }

    private DiagnosisTraceDtos.Response.ResultTrace toResultTrace(DiagnosisResult result) {
        return new DiagnosisTraceDtos.Response.ResultTrace(
                result.getId(),
                result.getDiseaseCode(),
                result.getDiseaseName(),
                result.getConfidence(),
                result.getRankNo(),
                result.getRiskLevel(),
                result.getEvidenceJson(),
                result.getCreatedAt()
        );
    }

    private DiagnosisTraceDtos.Response.FeedbackTrace toFeedbackTrace(DoctorFeedback feedback) {
        return new DiagnosisTraceDtos.Response.FeedbackTrace(
                feedback.getId(),
                feedback.getResultId(),
                feedback.getDoctorId(),
                feedback.getAction(),
                feedback.getModifiedValue(),
                feedback.getRejectReason(),
                feedback.getCreatedAt()
        );
    }

    private DiagnosisTraceDtos.Response.ModelInvocationTrace toModelInvocationTrace(ModelInvocationLog log) {
        return new DiagnosisTraceDtos.Response.ModelInvocationTrace(
                log.getId(),
                log.getProvider(),
                log.getRequestId(),
                log.getInputDigest(),
                log.getOutputDigest(),
                log.getLatencyMs(),
                log.getStatus(),
                log.getErrorMessage(),
                log.getCreatedAt()
        );
    }

    private DiagnosisTraceDtos.Response.AuditLogTrace toAuditLogTrace(AuditLog log) {
        return new DiagnosisTraceDtos.Response.AuditLogTrace(
                log.getId(),
                log.getUserId(),
                log.getUserRole(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getTraceId(),
                log.getCreatedAt()
        );
    }
}
