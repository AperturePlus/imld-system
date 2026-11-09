package xenosoft.imldintelligence.module.diagnoses.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosesApiDtos;
import xenosoft.imldintelligence.module.diagnoses.api.dto.ImldInferenceApiDtos;
import xenosoft.imldintelligence.module.careplan.api.service.ClinicalAlertService;
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
import xenosoft.imldintelligence.module.identity.internal.model.UserAccount;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;
import xenosoft.imldintelligence.module.identity.internal.repository.UserAccountRepository;
import xenosoft.imldintelligence.module.identity.internal.security.CurrentUserSubjectProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class DiagnosesCommandService {
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REVIEWED = "REVIEWED";

    private final DiagnosisSessionRepository sessionRepository;
    private final DiagnosisResultRepository resultRepository;
    private final DiagnosisRecommendationRepository recommendationRepository;
    private final DoctorFeedbackRepository feedbackRepository;
    private final ModelRegistryRepository modelRegistryRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;
    private final CurrentUserSubjectProvider currentUserSubjectProvider;
    private final ClinicalAlertService clinicalAlertService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModelRegistry resolveModel(Long tenantId, Long modelRegistryId) {
        if (modelRegistryId != null) {
            return modelRegistryRepository.findById(tenantId, modelRegistryId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "modelRegistryId does not exist"));
        }
        Optional<ModelRegistry> active = modelRegistryRepository.listByTenantId(tenantId).stream()
                .filter(m -> "ACTIVE".equalsIgnoreCase(m.getStatus()))
                .findFirst();
        if (active.isPresent()) {
            return active.get();
        }
        Optional<ModelRegistry> builtIn = modelRegistryRepository.findByModelCodeAndModelVersion(
                tenantId, "IMLD_XGBOOST", "v2_gene_clinical_fusion"
        );
        if (builtIn.isPresent()) {
            ModelRegistry existing = builtIn.get();
            if (!"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
                existing.setStatus("ACTIVE");
                modelRegistryRepository.update(existing);
            }
            return existing;
        }
        ModelRegistry created = new ModelRegistry();
        created.setTenantId(tenantId);
        created.setModelCode("IMLD_XGBOOST");
        created.setModelName("IMLD XGBoost Inference");
        created.setModelType("ML");
        created.setModelVersion("v2_gene_clinical_fusion");
        created.setProvider("LOCAL");
        created.setStatus("ACTIVE");
        created.setReleasedAt(now());
        modelRegistryRepository.saveIfAbsent(created);
        return modelRegistryRepository.findByModelCodeAndModelVersion(
                        tenantId, "IMLD_XGBOOST", "v2_gene_clinical_fusion")
                .orElseThrow(() -> new IllegalStateException("Failed to create or load built-in model registry"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiagnosisSession createRunningSession(Long tenantId,
                                                 Long patientId,
                                                 Long encounterId,
                                                 Long doctorId,
                                                 Long modelRegistryId,
                                                 String triggeredBy,
                                                 String traceId,
                                                 com.fasterxml.jackson.databind.JsonNode snapshot) {
        DiagnosisSession session = new DiagnosisSession();
        session.setTenantId(tenantId);
        session.setPatientId(patientId);
        session.setEncounterId(encounterId);
        session.setDoctorId(doctorId);
        session.setTriggeredBy(normalize(triggeredBy, "MANUAL"));
        session.setModelRegistryId(modelRegistryId);
        session.setInputSnapshot(snapshot);
        session.setStatus(STATUS_RUNNING);
        session.setStartedAt(now());
        session.setTraceId(traceId);
        sessionRepository.save(session);
        return session;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiagnosisSession completeSession(Long tenantId,
                                            Long sessionId,
                                            ImldInferenceApiDtos.Response.PredictData prediction) {
        DiagnosisSession session = sessionRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis session not found"));
        DiagnosisResult result = savePrediction(tenantId, session, prediction);
        session.setStatus(STATUS_COMPLETED);
        session.setCompletedAt(now());
        sessionRepository.update(session);
        try {
            clinicalAlertService.recordDiagnosisAlert(
                    tenantId,
                    session.getPatientId(),
                    session.getId(),
                    result.getId(),
                    session.getDoctorId(),
                    result.getDiseaseCode(),
                    result.getDiseaseName(),
                    result.getRiskLevel(),
                    result.getConfidence() == null ? 0D : result.getConfidence()
            );
        } catch (RuntimeException ex) {
            log.warn("Failed to generate high-risk alert for diagnosis session {}", session.getId(), ex);
        }
        return session;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiagnosisSession failSession(Long tenantId, Long sessionId) {
        DiagnosisSession session = sessionRepository.findById(tenantId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis session not found"));
        session.setStatus(STATUS_FAILED);
        session.setCompletedAt(now());
        sessionRepository.update(session);
        return session;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DiagnosisSession submitDoctorFeedback(Long tenantId,
                                                 DiagnosesApiDtos.Request.SubmitDoctorFeedbackRequest request) {
        if (request == null || request.sessionId() == null || request.resultId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId and resultId are required");
        }
        DiagnosisSession session = sessionRepository.findById(tenantId, request.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis session not found"));
        DiagnosisResult result = resultRepository.findById(tenantId, request.resultId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "resultId does not exist"));
        if (!Objects.equals(result.getSessionId(), session.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "resultId does not belong to sessionId");
        }

        DoctorFeedback feedback = new DoctorFeedback();
        feedback.setTenantId(tenantId);
        feedback.setSessionId(session.getId());
        feedback.setResultId(result.getId());
        Long feedbackDoctorId = request.doctorId() != null
                ? resolveDoctorId(tenantId, request.doctorId())
                : (session.getDoctorId() != null ? session.getDoctorId() : resolveDoctorId(tenantId, null));
        feedback.setDoctorId(feedbackDoctorId);
        feedback.setAction(normalize(request.action(), "MODIFY"));
        feedback.setModifiedValue(request.modifiedValue());
        feedback.setRejectReason(trimToNull(request.rejectReason()));
        feedbackRepository.save(feedback);

        if (Set.of("ACCEPT", "MODIFY").contains(feedback.getAction())) {
            session.setStatus(STATUS_REVIEWED);
            sessionRepository.update(session);
        }
        return session;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModelRegistry registerModel(Long tenantId, DiagnosesApiDtos.Request.RegisterModelRequest request) {
        if (modelRegistryRepository.findByModelCodeAndModelVersion(tenantId, request.modelCode(), request.modelVersion()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "modelCode + modelVersion already exists");
        }
        ModelRegistry model = new ModelRegistry();
        model.setTenantId(tenantId);
        model.setModelCode(request.modelCode().trim());
        model.setModelName(request.modelName().trim());
        model.setModelType(request.modelType().trim());
        model.setModelVersion(request.modelVersion().trim());
        model.setProvider(request.provider().trim());
        model.setStatus(normalize(request.status(), "ACTIVE"));
        model.setReleasedAt(request.releasedAt());
        modelRegistryRepository.save(model);
        return model;
    }

    private Long resolveDoctorId(Long tenantId, Long doctorId) {
        if (doctorId != null) {
            return userAccountRepository.findById(tenantId, doctorId).map(UserAccount::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "doctorId does not exist"));
        }
        // Attribute to the authenticated doctor rather than an arbitrary tenant user,
        // so audit/responsibility ownership reflects who actually performed the action.
        return currentUserSubjectProvider.getCurrentSubject()
                .map(UserSubject::userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No authenticated doctor to attribute feedback to"));
    }

    private DiagnosisResult savePrediction(
            Long tenantId, DiagnosisSession session, ImldInferenceApiDtos.Response.PredictData p) {
        String diseaseCode = "IMLD_RISK";
        String diseaseName = "遗传代谢性肝病风险提示";
        List<String> genes = p.geneAbnormalities() == null
                ? List.of()
                : p.geneAbnormalities().stream()
                        .map(ImldInferenceApiDtos.Response.GeneAbnormality::gene)
                        .filter(Objects::nonNull)
                        .toList();
        if (genes.contains("ATP7B")) {
            diseaseCode = "WILSON_DISEASE";
            diseaseName = "肝豆状核变性（Wilson病）";
        } else if (genes.contains("HFE")) {
            diseaseCode = "HEMOCHROMATOSIS";
            diseaseName = "遗传性血色病";
        }

        DiagnosisResult result = new DiagnosisResult();
        result.setTenantId(tenantId);
        result.setSessionId(session.getId());
        result.setDiseaseCode(diseaseCode);
        result.setDiseaseName(diseaseName);
        result.setConfidence(p.riskProbability());
        result.setRankNo(1);
        result.setRiskLevel(p.riskLevel());
        ObjectNode evidence = objectMapper.createObjectNode();
        evidence.set("inference", objectMapper.valueToTree(p));
        evidence.put("generated_at", now().toString());
        result.setEvidenceJson(evidence);
        result.setIsDisplayToPatient(Boolean.FALSE);
        resultRepository.save(result);

        List<String> suggestions = p.suggestions() == null ? List.of() : p.suggestions();
        List<DiagnosisRecommendation> recommendations = new ArrayList<>();
        int priority = 10;
        for (String suggestion : suggestions) {
            if (suggestion == null || suggestion.isBlank()) continue;
            DiagnosisRecommendation rec = new DiagnosisRecommendation();
            rec.setTenantId(tenantId);
            rec.setSessionId(session.getId());
            rec.setRecType(recType(suggestion));
            rec.setContent(suggestion);
            rec.setPriority(priority);
            rec.setReason("IMLD_MODEL");
            recommendations.add(rec);
            priority += 10;
        }
        recommendationRepository.saveBatch(recommendations);
        return result;
    }

    private String recType(String text) {
        String n = text.toLowerCase(Locale.ROOT);
        if (n.contains("基因") || n.contains("gene")) return "GENETIC";
        if (n.contains("饮食") || n.contains("膳食") || n.contains("diet")) return "DIET";
        if (n.contains("检查") || n.contains("检验") || n.contains("exam")) return "EXAM";
        return "FOLLOWUP";
    }

    private String normalize(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
