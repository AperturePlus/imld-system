package xenosoft.imldintelligence.module.clinical.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.module.clinical.api.dto.WebPatientApiDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class WebPatientController implements WebPatientControllerContract {
    private static final String STATUS_DIAGNOSED = "已诊断";
    private static final String STATUS_UNDIAGNOSED = "未诊断";

    private final PatientRepository patientRepository;
    private final DiagnosisSessionRepository diagnosisSessionRepository;
    private final DiagnosisResultRepository diagnosisResultRepository;

    @Override
    public ApiResponse<WebPatientApiDtos.Response.PatientListResponse> listPatients(Long tenantId, String keyword) {
        String normalizedKeyword = trimToNull(keyword);
        List<Patient> patients = patientRepository.listByTenantId(tenantId).stream()
                .filter(patient -> "ACTIVE".equalsIgnoreCase(patient.getStatus()))
                .filter(patient -> matchesKeyword(patient, normalizedKeyword))
                .sorted(Comparator.comparing(Patient::getPatientNo, Comparator.nullsLast(String::compareTo)))
                .toList();

        Map<Long, PatientDiagnosisSummary> diagnosisByPatient =
                buildDiagnosisSummaries(tenantId, patients.stream().map(Patient::getId).toList());

        List<WebPatientApiDtos.Response.PatientSummary> items = patients.stream()
                .map(patient -> {
                    PatientDiagnosisSummary diagnosis = diagnosisByPatient.getOrDefault(
                            patient.getId(), new PatientDiagnosisSummary(STATUS_UNDIAGNOSED, null));
                    return new WebPatientApiDtos.Response.PatientSummary(
                            patient.getPatientNo(),
                            patient.getPatientName(),
                            normalizeGender(patient.getGender()),
                            ageOf(patient),
                            diagnosis.riskLevel(),
                            diagnosis.aiStatus(),
                            ""
                    );
                })
                .toList();
        return ApiResponse.success(new WebPatientApiDtos.Response.PatientListResponse(items));
    }

    private boolean matchesKeyword(Patient patient, String keyword) {
        if (keyword == null) {
            return true;
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return contains(patient.getPatientNo(), normalized) || contains(patient.getPatientName(), normalized);
    }

    private boolean contains(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    /**
     * 批量构建患者诊断摘要。
     *
     * <p>一次查询患者会话、一次查询会话结果，再在内存中按患者分组取最新风险等级，
     * 避免对每个患者分别查询数据库。</p>
     */
    private Map<Long, PatientDiagnosisSummary> buildDiagnosisSummaries(Long tenantId, List<Long> patientIds) {
        if (patientIds.isEmpty()) {
            return Map.of();
        }

        // 一次查出这些患者的所有权威会话（COMPLETED/REVIEWED）
        List<DiagnosisSession> sessions = diagnosisSessionRepository
                .listByPatientIds(tenantId, patientIds).stream()
                .filter(this::isAuthoritativeDiagnosisSession)
                .toList();

        // 一次查出这些会话的所有结果
        List<Long> sessionIds = sessions.stream().map(DiagnosisSession::getId).toList();
        Map<Long, List<DiagnosisResult>> resultsBySession = diagnosisResultRepository
                .listBySessionIds(tenantId, sessionIds).stream()
                .collect(Collectors.groupingBy(DiagnosisResult::getSessionId));

        Map<Long, List<DiagnosisSession>> sessionsByPatient =
                sessions.stream().collect(Collectors.groupingBy(DiagnosisSession::getPatientId));

        Map<Long, PatientDiagnosisSummary> summaries = new HashMap<>();
        for (Map.Entry<Long, List<DiagnosisSession>> entry : sessionsByPatient.entrySet()) {
            DiagnosisSession latest = entry.getValue().stream()
                    .max(Comparator
                            .comparing(this::diagnosisSessionSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(DiagnosisSession::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
            if (latest == null) {
                continue;
            }
            String riskLevel = resultsBySession.getOrDefault(latest.getId(), List.of()).stream()
                    .map(DiagnosisResult::getRiskLevel)
                    .map(this::toFrontendRiskLevel)
                    .findFirst()
                    .orElse(null);
            if (riskLevel == null) {
                summaries.put(entry.getKey(), new PatientDiagnosisSummary(STATUS_UNDIAGNOSED, null));
            } else {
                summaries.put(entry.getKey(), new PatientDiagnosisSummary(STATUS_DIAGNOSED, riskLevel));
            }
        }
        return summaries;
    }

    private OffsetDateTime diagnosisSessionSortTime(DiagnosisSession session) {
        return session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
    }

    private boolean isAuthoritativeDiagnosisSession(DiagnosisSession session) {
        return session != null && (
                "COMPLETED".equalsIgnoreCase(session.getStatus()) ||
                        "REVIEWED".equalsIgnoreCase(session.getStatus())
        );
    }

    private String toFrontendRiskLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return "中";
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("HIGH") || riskLevel.contains("高")) {
            return "高";
        }
        if (normalized.contains("LOW") || riskLevel.contains("低")) {
            return "低";
        }
        return "中";
    }

    private int ageOf(Patient patient) {
        LocalDate birthDate = patient.getBirthDate();
        if (birthDate == null) {
            return 0;
        }
        return Math.max(0, (int) ChronoUnit.YEARS.between(birthDate, LocalDate.now(ZoneOffset.UTC)));
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return "--";
        }
        String normalized = gender.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("male") || normalized.equals("m")) {
            return "男";
        }
        if (normalized.equals("female") || normalized.equals("f")) {
            return "女";
        }
        return gender.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record PatientDiagnosisSummary(String aiStatus, String riskLevel) {
    }
}
