package xenosoft.imldintelligence.module.screening.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.module.careplan.api.service.ClinicalAlertService;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.FollowupTaskRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.screening.api.dto.ScreeningApiDtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class WebScreeningController implements WebScreeningControllerContract {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Set<String> ACTIVE_ALERT_STATUSES = Set.of("OPEN", "ACKNOWLEDGED");

    private final ClinicalAlertService clinicalAlertService;
    private final AlertEventRepository alertEventRepository;
    private final FollowupTaskRepository followupTaskRepository;
    private final PatientRepository patientRepository;

    @Override
    public ApiResponse<ScreeningApiDtos.Response.ScreeningOverviewResponse> getOverview(Long tenantId,
                                                                                        String from,
                                                                                        String to) {
        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }

        clinicalAlertService.syncHighRiskAlerts(tenantId);
        List<Patient> patients = patientRepository.listByTenantId(tenantId);
        Map<Long, Patient> patientMap = patients.stream()
                .collect(Collectors.toMap(Patient::getId, patient -> patient, (left, right) -> left));
        List<AlertEvent> alerts = alertEventRepository.listByTenantId(tenantId).stream()
                .filter(alert -> alert.getStatus() != null
                        && ACTIVE_ALERT_STATUSES.contains(alert.getStatus().toUpperCase()))
                .sorted(Comparator
                        .comparing(AlertEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AlertEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        long taskCount = followupTaskRepository.listByTenantId(tenantId).size();
        List<ScreeningApiDtos.Response.HighRiskPatientItem> highRiskPatients = alerts.stream()
                .limit(20)
                .map(alert -> toHighRiskItem(alert, patientMap.get(alert.getPatientId())))
                .toList();

        return ApiResponse.success(new ScreeningApiDtos.Response.ScreeningOverviewResponse(
                nowUtc(),
                List.of(
                        statCard("累计筛查总人数", "User", "#409EFF", null,
                                BigDecimal.valueOf(patients.size())),
                        statCard("检出高危/阳性", "WarnTriangleFilled", "#f56c6c", "例",
                                BigDecimal.valueOf(alerts.size())),
                        statCard("基因突变携带率", "TrendCharts", "#e6a23c", "%", ZERO),
                        statCard("AI 干预采纳数", "MagicStick", "#67c23a", "次",
                                BigDecimal.valueOf(taskCount))
                ),
                riskDistribution(alerts.size(), patients.size()),
                List.of(),
                new ScreeningApiDtos.Response.AiEfficiencyMetrics(ZERO, "0%", "--"),
                highRiskPatients
        ));
    }

    private List<ScreeningApiDtos.Response.RiskDistributionItem> riskDistribution(int highRiskCount, int totalCount) {
        if (highRiskCount <= 0) {
            return List.of();
        }
        BigDecimal percentage = totalCount <= 0
                ? ZERO
                : BigDecimal.valueOf(highRiskCount)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(totalCount), 2, java.math.RoundingMode.HALF_UP);
        return List.of(new ScreeningApiDtos.Response.RiskDistributionItem(
                "高危", highRiskCount, percentage, "#f56c6c"));
    }

    private ScreeningApiDtos.Response.HighRiskPatientItem toHighRiskItem(AlertEvent alert, Patient patient) {
        String diseaseName = alert.getTriggerDetail() == null
                ? null : alert.getTriggerDetail().path("diseaseName").asText(null);
        String clue = alert.getTriggerDetail() == null
                ? null : alert.getTriggerDetail().path("clue").asText(null);
        return new ScreeningApiDtos.Response.HighRiskPatientItem(
                alert.getCreatedAt() == null ? null : alert.getCreatedAt().toLocalDate().toString(),
                patient == null ? "患者#" + alert.getPatientId() : patient.getPatientName(),
                patient == null ? null : ageOf(patient),
                clue,
                diseaseName == null ? "高危阳性" : diseaseName,
                alert.getId(),
                alert.getPatientId(),
                patient == null ? null : patient.getPatientNo(),
                alert.getSourceSessionId(),
                alert.getSourceResultId(),
                patient != null && patient.getMobileEncrypted() != null
                        && !patient.getMobileEncrypted().isBlank()
        );
    }

    private ScreeningApiDtos.Response.StatCardItem statCard(
            String title, String icon, String color, String suffix, BigDecimal value) {
        return new ScreeningApiDtos.Response.StatCardItem(title, value, color, icon, ZERO, suffix);
    }

    private Integer ageOf(Patient patient) {
        if (patient.getBirthDate() == null) {
            return null;
        }
        return (int) Math.max(0, ChronoUnit.YEARS.between(
                patient.getBirthDate(), LocalDate.now(ZoneOffset.UTC)));
    }

    private LocalDate parseDate(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be ISO-8601 date (yyyy-MM-dd)", ex);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString();
    }
}
