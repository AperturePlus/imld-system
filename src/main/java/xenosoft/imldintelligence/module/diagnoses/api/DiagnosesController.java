package xenosoft.imldintelligence.module.diagnoses.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import xenosoft.imldintelligence.common.RequireAnyRole;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.audit.internal.aop.AuditedOperation;
import xenosoft.imldintelligence.module.audit.internal.aop.SensitiveAccessed;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContext;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContextHolder;
import xenosoft.imldintelligence.module.audit.internal.service.AuditTrailService;
import xenosoft.imldintelligence.module.audit.internal.service.command.ModelInvocationRecordCommand;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosisTraceDtos;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosesApiDtos;
import xenosoft.imldintelligence.module.diagnoses.api.dto.ImldInferenceApiDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisRecommendation;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DoctorFeedback;
import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisRecommendationRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DoctorFeedbackRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.ModelRegistryRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.query.DiagnosisSessionQuery;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.query.ModelRegistryQuery;
import xenosoft.imldintelligence.module.diagnoses.internal.service.DiagnosesCommandService;
import xenosoft.imldintelligence.module.diagnoses.internal.service.DiagnosisTraceService;
import xenosoft.imldintelligence.module.diagnoses.internal.service.ImldFeatureBuilder;
import xenosoft.imldintelligence.module.diagnoses.internal.service.ImldInferenceService;
import xenosoft.imldintelligence.module.identity.internal.model.Encounter;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.model.UserAccount;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;
import xenosoft.imldintelligence.module.identity.internal.repository.EncounterRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.UserAccountRepository;
import xenosoft.imldintelligence.module.identity.internal.security.CurrentUserSubjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
public class DiagnosesController implements DiagnosesControllerContract {
    private static final Logger LOG = LoggerFactory.getLogger(DiagnosesController.class);
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private final DiagnosisSessionRepository sessionRepository;
    private final DiagnosisResultRepository resultRepository;
    private final DiagnosisRecommendationRepository recommendationRepository;
    private final DoctorFeedbackRepository feedbackRepository;
    private final ModelRegistryRepository modelRegistryRepository;
    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;
    private final UserAccountRepository userAccountRepository;
    private final DiagnosesCommandService diagnosesCommandService;
    private final ImldInferenceService inferenceService;
    private final ImldFeatureBuilder featureBuilder;
    private final CurrentUserSubjectProvider currentUserSubjectProvider;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;
    private final DiagnosisTraceService diagnosisTraceService;

    /**
     * 分页查询诊断会话。
     *
     * <p>先分页查询会话主表，再一次性批量查询结果、建议、反馈三个子表，
     * 按会话标识分组后组装响应，避免逐条会话查询带来的 N+1 问题。</p>
     */
    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse>> listSessions(
            Long tenantId,
            DiagnosesApiDtos.Query.SessionPageQuery query,
            PageQueryRequest pageQuery) {
        DiagnosesApiDtos.Query.SessionPageQuery q = query == null
                ? new DiagnosesApiDtos.Query.SessionPageQuery(null, null, null, null, null, null, null)
                : query;

        int page = pageQuery == null || pageQuery.page() == null ? DEFAULT_PAGE : Math.max(pageQuery.page(), 0);
        int size = pageQuery == null || pageQuery.size() == null ? DEFAULT_SIZE : Math.min(Math.max(pageQuery.size(), 1), 200);

        DiagnosisSessionQuery sessionQuery = new DiagnosisSessionQuery();
        sessionQuery.setTenantId(tenantId);
        sessionQuery.setPatientId(q.patientId());
        sessionQuery.setEncounterId(q.encounterId());
        sessionQuery.setDoctorId(q.doctorId());
        sessionQuery.setStatus(q.status());
        sessionQuery.setTriggeredBy(q.triggeredBy());
        sessionQuery.setStartedFrom(q.startedFrom());
        sessionQuery.setStartedTo(q.startedTo());

        long total = sessionRepository.count(sessionQuery);
        long offset = (long) page * size;
        List<DiagnosisSession> sessions = sessionRepository.query(sessionQuery, offset, size);

        // 一次性批量查出三个子表，避免对每条 session 再逐条查询
        List<Long> sessionIds = sessions.stream().map(DiagnosisSession::getId).toList();
        Map<Long, List<DiagnosisResult>> resultsBySession = resultRepository
                .listBySessionIds(tenantId, sessionIds).stream()
                .collect(Collectors.groupingBy(DiagnosisResult::getSessionId));
        Map<Long, List<DiagnosisRecommendation>> recsBySession = recommendationRepository
                .listBySessionIds(tenantId, sessionIds).stream()
                .collect(Collectors.groupingBy(DiagnosisRecommendation::getSessionId));
        Map<Long, List<DoctorFeedback>> feedbacksBySession = feedbackRepository
                .listBySessionIds(tenantId, sessionIds).stream()
                .collect(Collectors.groupingBy(DoctorFeedback::getSessionId));

        List<DiagnosesApiDtos.Response.DiagnosisSessionResponse> items = sessions.stream()
                .map(s -> assembleSessionResponse(s,
                        resultsBySession.getOrDefault(s.getId(), List.of()),
                        recsBySession.getOrDefault(s.getId(), List.of()),
                        feedbacksBySession.getOrDefault(s.getId(), List.of())))
                .toList();
        return ApiResponse.success(new PagedResultResponse<>(page, size, total, items));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> getSession(Long tenantId, Long sessionId) {
        DiagnosisSession session = sessionRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis session not found"));
        return ApiResponse.success(toSessionResponse(tenantId, session));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "DIAGNOSIS_START", resourceType = "DIAGNOSIS_SESSION",
            resourceIdExpression = "#result.data.id()")
    public ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> startSession(
            Long tenantId,
            DiagnosesApiDtos.Request.StartDiagnosisSessionRequest request) {
        if (request == null || request.patientId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        Patient patient = patientRepository.findById(tenantId, request.patientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId does not exist"));
        Encounter encounter = resolveEncounter(tenantId, request.patientId(), request.encounterId());
        Long doctorId = resolveDoctorId(tenantId, request.doctorId(), encounter);
        ModelRegistry model = diagnosesCommandService.resolveModel(tenantId, request.modelRegistryId());
        AuditContext auditContext = AuditContextHolder.get();
        String traceId = auditContext == null ? null : auditContext.getTraceId();
        ObjectNode derivedSourceSnapshot = featureBuilder.buildDerivedSourceSnapshot(tenantId, patient, encounter);
        ImldInferenceApiDtos.Request.ImldPredictRequest inferenceRequest =
                featureBuilder.buildInferenceRequest(tenantId, patient, encounter, request.inputSnapshot(), derivedSourceSnapshot);
        DiagnosisSession session = diagnosesCommandService.createRunningSession(
                tenantId,
                patient.getId(),
                encounter == null ? request.encounterId() : encounter.getId(),
                doctorId,
                model.getId(),
                request.triggeredBy(),
                traceId,
                featureBuilder.buildSnapshot(request.inputSnapshot(), inferenceRequest, derivedSourceSnapshot)
        );

        long started = System.currentTimeMillis();
        ImldInferenceApiDtos.Response.PredictData prediction;
        try {
            prediction = inferenceService.predict(inferenceRequest);
            session = diagnosesCommandService.completeSession(tenantId, session.getId(), prediction);
        } catch (RuntimeException ex) {
            try {
                session = diagnosesCommandService.failSession(tenantId, session.getId());
            } catch (RuntimeException persistEx) {
                ex.addSuppressed(persistEx);
            }
            try {
                recordModelInvocation(tenantId, session, model, traceId, inferenceRequest, null,
                        elapsedMillis(started), "FAILED", ex.getClass().getSimpleName());
            } catch (RuntimeException auditEx) {
                ex.addSuppressed(auditEx);
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IMLD inference failed", ex);
        }
        try {
            recordModelInvocation(tenantId, session, model, traceId, inferenceRequest, prediction,
                    elapsedMillis(started), "SUCCESS", null);
        } catch (RuntimeException auditEx) {
            // 模型调用已完成，审计写入失败不能把已完成的诊断会话改写成 FAILED。
            LOG.error("Failed to persist successful model invocation audit for session {}", session.getId(), auditEx);
        }
        return ApiResponse.success(toSessionResponse(tenantId, session));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "DIAGNOSIS_FEEDBACK", resourceType = "DIAGNOSIS_SESSION",
            resourceIdExpression = "#result.data.id()")
    public ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> submitDoctorFeedback(
            Long tenantId,
            DiagnosesApiDtos.Request.SubmitDoctorFeedbackRequest request) {
        DiagnosisSession session = diagnosesCommandService.submitDoctorFeedback(tenantId, request);
        return ApiResponse.success(toSessionResponse(tenantId, session));
    }

    /**
     * 查询诊断决策链追溯信息。
     *
     * <p>聚合会话、模型、诊断结果、医生反馈、模型调用日志和审计日志，
     * 供合规审计和问题追溯使用。</p>
     */
    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @SensitiveAccessed(
            sensitiveType = "DIAGNOSIS_TRACE",
            resourceType = "DIAGNOSIS_SESSION",
            resourceIdExpression = "#p1",
            reasonExpression = "'diagnosis-decision-trace'"
    )
    public ApiResponse<DiagnosisTraceDtos.Response.DiagnosisTraceResponse> getSessionTrace(
            Long tenantId, Long sessionId) {
        DiagnosisTraceDtos.Response.DiagnosisTraceResponse trace =
                diagnosisTraceService.getTrace(tenantId, sessionId);
        enforceTraceAccess(trace.session());
        return ApiResponse.success(trace);
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<DiagnosesApiDtos.Response.ModelRegistryResponse>> listModels(
            Long tenantId,
            DiagnosesApiDtos.Query.ModelRegistryPageQuery query,
            PageQueryRequest pageQuery) {
        DiagnosesApiDtos.Query.ModelRegistryPageQuery q = query == null
                ? new DiagnosesApiDtos.Query.ModelRegistryPageQuery(null, null, null)
                : query;

        int page = pageQuery == null || pageQuery.page() == null ? DEFAULT_PAGE : Math.max(pageQuery.page(), 0);
        int size = pageQuery == null || pageQuery.size() == null ? DEFAULT_SIZE : Math.min(Math.max(pageQuery.size(), 1), 200);

        ModelRegistryQuery modelQuery = new ModelRegistryQuery();
        modelQuery.setTenantId(tenantId);
        modelQuery.setProvider(q.provider());
        modelQuery.setModelType(q.modelType());
        modelQuery.setStatus(q.status());

        long total = modelRegistryRepository.count(modelQuery);
        long offset = (long) page * size;
        List<DiagnosesApiDtos.Response.ModelRegistryResponse> items = modelRegistryRepository
                .query(modelQuery, offset, size).stream()
                .map(this::toModelResponse)
                .toList();
        return ApiResponse.success(new PagedResultResponse<>(page, size, total, items));
    }

    @Override
    @RequireAnyRole({ROLE_SYSTEM_ADMIN})
    public ApiResponse<DiagnosesApiDtos.Response.ModelRegistryResponse> registerModel(
            Long tenantId,
            DiagnosesApiDtos.Request.RegisterModelRequest request) {
        ModelRegistry model = diagnosesCommandService.registerModel(tenantId, request);
        return ApiResponse.success(toModelResponse(model));
    }

    private DiagnosesApiDtos.Response.DiagnosisSessionResponse toSessionResponse(Long tenantId, DiagnosisSession s) {
        return assembleSessionResponse(s,
                resultRepository.listBySessionId(tenantId, s.getId()),
                recommendationRepository.listBySessionId(tenantId, s.getId()),
                feedbackRepository.listBySessionId(tenantId, s.getId()));
    }

    /**
     * 组装单个诊断会话的响应。
     *
     * <p>结果、建议、反馈由调用方传入，便于批量查询场景复用同一组装入口，
     * 避免在循环内重复查询子表。</p>
     */
    private DiagnosesApiDtos.Response.DiagnosisSessionResponse assembleSessionResponse(
            DiagnosisSession s,
            List<DiagnosisResult> results,
            List<DiagnosisRecommendation> recommendations,
            List<DoctorFeedback> feedbacks) {
        return new DiagnosesApiDtos.Response.DiagnosisSessionResponse(
                s.getId(), s.getPatientId(), s.getEncounterId(), s.getDoctorId(), s.getTriggeredBy(), s.getModelRegistryId(),
                s.getStatus(), s.getStartedAt(), s.getCompletedAt(),
                results.stream().map(r -> new DiagnosesApiDtos.Shared.DiagnosisResultItem(
                        r.getId(), r.getDiseaseCode(), r.getDiseaseName(), r.getConfidence(), r.getRankNo(), r.getRiskLevel(),
                        r.getEvidenceJson(), r.getIsDisplayToPatient(), r.getCreatedAt())).toList(),
                recommendations.stream().map(r -> new DiagnosesApiDtos.Shared.DiagnosisRecommendationItem(
                        r.getId(), r.getRecType(), r.getContent(), r.getPriority(), r.getReason(), r.getCreatedAt())).toList(),
                feedbacks.stream().map(f -> new DiagnosesApiDtos.Shared.DoctorFeedbackItem(
                        f.getId(),
                        f.getResultId(),
                        f.getDoctorId(),
                        f.getAction(),
                        f.getModifiedValue(),
                        f.getRejectReason(),
                        f.getCreatedAt())).toList()
        );
    }

    private DiagnosesApiDtos.Response.ModelRegistryResponse toModelResponse(ModelRegistry m) {
        return new DiagnosesApiDtos.Response.ModelRegistryResponse(
                m.getId(), m.getModelCode(), m.getModelName(), m.getModelType(), m.getModelVersion(),
                m.getProvider(), m.getStatus(), m.getReleasedAt(), m.getCreatedAt()
        );
    }

    private Encounter resolveEncounter(Long tenantId, Long patientId, Long encounterId) {
        if (encounterId != null) {
            return encounterRepository.findById(tenantId, encounterId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "encounterId does not exist"));
        }
        return encounterRepository.listByPatientId(tenantId, patientId).stream()
                .sorted(Comparator.comparing(Encounter::getStartAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .findFirst().orElse(null);
    }

    private Long resolveDoctorId(Long tenantId, Long doctorId, Encounter encounter) {
        if (doctorId != null) {
            return userAccountRepository.findById(tenantId, doctorId).map(UserAccount::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId does not exist"));
        }
        if (encounter != null && encounter.getAttendingDoctorId() != null &&
                userAccountRepository.findById(tenantId, encounter.getAttendingDoctorId()).isPresent()) {
            return encounter.getAttendingDoctorId();
        }
        // Attribute to the authenticated doctor rather than an arbitrary tenant user,
        // so audit/responsibility ownership reflects who actually started the session.
        return currentUserSubjectProvider.getCurrentSubject()
                .map(UserSubject::userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No authenticated doctor to attribute session to"));
    }

    private void enforceTraceAccess(DiagnosisTraceDtos.Response.SessionTrace session) {
        UserSubject currentUser = currentUserSubjectProvider.requireCurrentSubject();
        if (currentUser.roleCodes().contains(ROLE_SYSTEM_ADMIN)) {
            return;
        }
        if (session == null || !Objects.equals(session.doctorId(), currentUser.userId())) {
            throw new AccessDeniedException("Doctors may only access traces for their own diagnosis sessions");
        }
    }

    /**
     * 记录一次模型调用审计日志。
     */
    private void recordModelInvocation(Long tenantId, DiagnosisSession session, ModelRegistry model,
                                       String traceId, Object input, Object output,
                                       int latencyMs, String status, String errorMessage) {
        ModelInvocationRecordCommand command = new ModelInvocationRecordCommand();
        command.setTenantId(tenantId);
        command.setSessionId(session.getId());
        command.setModelRegistryId(model.getId());
        command.setProvider(model.getProvider());
        command.setRequestId(traceId);
        command.setTraceId(traceId);
        command.setInputDigest(sha256Hex(input));
        command.setOutputDigest(sha256Hex(output));
        command.setLatencyMs(latencyMs);
        command.setStatus(status);
        command.setErrorMessage(errorMessage);
        auditTrailService.recordModelInvocation(command);
    }

    /**
     * 对请求或响应对象计算 SHA-256 摘要，避免审计日志保存明文。
     */
    private String sha256Hex(Object value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] payload = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return null;
        }
    }

    private int elapsedMillis(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, System.currentTimeMillis() - startedAt));
    }

}
