package xenosoft.imldintelligence.module.careplan.internal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xenosoft.imldintelligence.module.careplan.api.service.ClinicalAlertService;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 诊断高风险预警服务实现。
 */
@Service
@RequiredArgsConstructor
public class ClinicalAlertServiceImpl implements ClinicalAlertService {
    private static final String TRIGGER_TYPE = "DIAGNOSIS_HIGH_RISK";
    private static final Set<String> AUTHORITATIVE_STATUSES = Set.of("COMPLETED", "REVIEWED");
    private static final int MAX_SYNC_SESSIONS = 500;

    private final AlertEventRepository alertEventRepository;
    private final DiagnosisSessionRepository sessionRepository;
    private final DiagnosisResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void recordDiagnosisAlert(Long tenantId,
                                    Long patientId,
                                    Long sessionId,
                                    Long resultId,
                                    Long assignedTo,
                                    String diseaseCode,
                                    String diseaseName,
                                    String riskLevel,
                                    double confidence) {
        if (!isHighRisk(riskLevel, confidence)) {
            return;
        }
        AlertEvent event = buildAlert(
                tenantId, patientId, sessionId, resultId, assignedTo,
                diseaseCode, diseaseName, riskLevel, confidence);
        alertEventRepository.saveIfAbsent(event);
    }

    @Override
    @Transactional
    public void syncHighRiskAlerts(Long tenantId) {
        List<DiagnosisSession> sessions = sessionRepository.listByTenantId(tenantId).stream()
                .filter(session -> session.getStatus() != null
                        && AUTHORITATIVE_STATUSES.contains(session.getStatus().toUpperCase(Locale.ROOT)))
                .sorted(Comparator
                        .comparing(this::sortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DiagnosisSession::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .limit(MAX_SYNC_SESSIONS)
                .toList();
        if (sessions.isEmpty()) {
            return;
        }

        List<Long> sessionIds = sessions.stream().map(DiagnosisSession::getId).toList();
        Map<Long, List<DiagnosisResult>> resultsBySession = resultRepository
                .listBySessionIds(tenantId, sessionIds).stream()
                .collect(Collectors.groupingBy(DiagnosisResult::getSessionId));

        for (DiagnosisSession session : sessions) {
            for (DiagnosisResult result : resultsBySession.getOrDefault(session.getId(), List.of())) {
                recordDiagnosisAlert(
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
            }
        }
    }

    private AlertEvent buildAlert(Long tenantId,
                                  Long patientId,
                                  Long sessionId,
                                  Long resultId,
                                  Long assignedTo,
                                  String diseaseCode,
                                  String diseaseName,
                                  String riskLevel,
                                  double confidence) {
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("sessionId", sessionId);
        detail.put("resultId", resultId);
        detail.put("diseaseCode", diseaseCode == null ? "IMLD_RISK" : diseaseCode);
        detail.put("diseaseName", diseaseName == null ? "遗传代谢性肝病高风险" : diseaseName);
        detail.put("riskLevel", riskLevel);
        detail.put("riskProbability", confidence);
        detail.put("clue", (diseaseName == null ? "高风险阳性" : diseaseName)
                + " / " + (riskLevel == null ? "HIGH" : riskLevel));

        AlertEvent event = new AlertEvent();
        event.setTenantId(tenantId);
        event.setPatientId(patientId);
        event.setSourceSessionId(sessionId);
        event.setSourceResultId(resultId);
        event.setTriggerType(TRIGGER_TYPE);
        event.setTriggerDetail(detail);
        event.setSeverity("CRITICAL");
        event.setStatus("OPEN");
        event.setAssignedTo(assignedTo);
        event.setCreatedAt(now());
        return event;
    }

    private boolean isHighRisk(String riskLevel, double confidence) {
        String normalized = riskLevel == null ? "" : riskLevel.toUpperCase(Locale.ROOT);
        return confidence >= 0.7
                || riskLevel != null && riskLevel.contains("高")
                || normalized.contains("HIGH");
    }

    private OffsetDateTime sortTime(DiagnosisSession session) {
        return session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }
}
