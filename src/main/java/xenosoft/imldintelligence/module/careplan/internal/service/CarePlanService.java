package xenosoft.imldintelligence.module.careplan.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.careplan.api.dto.CarePlanApiDtos;
import xenosoft.imldintelligence.module.careplan.api.service.ClinicalAlertService;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertAction;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.model.CarePlan;
import xenosoft.imldintelligence.module.careplan.internal.model.FollowupTask;
import xenosoft.imldintelligence.module.careplan.internal.model.PatientReportedData;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertActionRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.CarePlanRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.FollowupTaskRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.PatientReportedDataRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.security.CurrentUserSubjectProvider;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 随访计划、任务、预警处置和患者上报业务服务。
 */
@Service
@RequiredArgsConstructor
public class CarePlanService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_OPEN = "OPEN";

    private final CarePlanRepository carePlanRepository;
    private final FollowupTaskRepository followupTaskRepository;
    private final AlertEventRepository alertEventRepository;
    private final AlertActionRepository alertActionRepository;
    private final PatientReportedDataRepository patientReportedDataRepository;
    private final PatientRepository patientRepository;
    private final CurrentUserSubjectProvider currentUserSubjectProvider;
    private final ClinicalAlertService clinicalAlertService;

    @Transactional(readOnly = true)
    public PagedResultResponse<CarePlanApiDtos.Response.CarePlanResponse> listCarePlans(
            Long tenantId,
            CarePlanApiDtos.Query.CarePlanPageQuery query,
            PageQueryRequest pageQuery) {
        CarePlanApiDtos.Query.CarePlanPageQuery q = query == null
                ? new CarePlanApiDtos.Query.CarePlanPageQuery(null, null, null, null, null, null)
                : query;
        List<CarePlan> matching = carePlanRepository.listByTenantId(tenantId).stream()
                .filter(plan -> q.patientId() == null || Objects.equals(q.patientId(), plan.getPatientId()))
                .filter(plan -> matches(plan.getDiseaseCode(), q.diseaseCode()))
                .filter(plan -> matches(plan.getPlanType(), q.planType()))
                .filter(plan -> matches(plan.getStatus(), q.status()))
                .filter(plan -> q.startFrom() == null
                        || plan.getStartDate() != null && !plan.getStartDate().isBefore(q.startFrom()))
                .filter(plan -> q.startTo() == null
                        || plan.getStartDate() != null && !plan.getStartDate().isAfter(q.startTo()))
                .sorted(Comparator
                        .comparing(CarePlan::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(CarePlan::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream().map(this::toCarePlanResponse).toList()
        );
    }

    @Transactional
    public CarePlanApiDtos.Response.CarePlanResponse createCarePlan(
            Long tenantId,
            CarePlanApiDtos.Request.CreateCarePlanRequest request) {
        Patient patient = requirePatient(tenantId, request.patientId());
        Long createdBy = resolveCurrentUserId();
        CarePlan plan = createPlan(
                tenantId,
                patient.getId(),
                request.diseaseCode(),
                request.planType(),
                request.enrollmentType(),
                request.templateId(),
                request.startDate(),
                request.endDate(),
                createdBy
        );
        return toCarePlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public PagedResultResponse<CarePlanApiDtos.Response.FollowupTaskResponse> listFollowupTasks(
            Long tenantId,
            CarePlanApiDtos.Query.FollowupTaskPageQuery query,
            PageQueryRequest pageQuery) {
        CarePlanApiDtos.Query.FollowupTaskPageQuery q = query == null
                ? new CarePlanApiDtos.Query.FollowupTaskPageQuery(null, null, null, null, null, null, null)
                : query;
        List<FollowupTask> candidates = q.carePlanId() != null
                ? followupTaskRepository.listByCarePlanId(tenantId, q.carePlanId())
                : q.patientId() != null
                ? followupTaskRepository.listByPatientId(tenantId, q.patientId())
                : followupTaskRepository.listByTenantId(tenantId);
        List<FollowupTask> matching = candidates.stream()
                .filter(task -> q.patientId() == null || Objects.equals(q.patientId(), task.getPatientId()))
                .filter(task -> matches(task.getTaskType(), q.taskType()))
                .filter(task -> matches(task.getStatus(), q.status()))
                .filter(task -> q.assignedTo() == null || Objects.equals(q.assignedTo(), task.getAssignedTo()))
                .filter(task -> q.scheduledFrom() == null
                        || task.getScheduledAt() != null && !task.getScheduledAt().isBefore(q.scheduledFrom()))
                .filter(task -> q.scheduledTo() == null
                        || task.getScheduledAt() != null && !task.getScheduledAt().isAfter(q.scheduledTo()))
                .sorted(Comparator
                        .comparing(FollowupTask::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FollowupTask::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream().map(this::toFollowupTaskResponse).toList()
        );
    }

    @Transactional
    public CarePlanApiDtos.Response.FollowupTaskResponse scheduleFollowupTask(
            Long tenantId,
            CarePlanApiDtos.Request.ScheduleFollowupTaskRequest request) {
        CarePlan carePlan = carePlanRepository.findById(tenantId, request.carePlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "carePlanId does not exist"));
        if (request.patientId() != null && !Objects.equals(carePlan.getPatientId(), request.patientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId does not belong to carePlanId");
        }
        FollowupTask task = createTask(
                tenantId,
                carePlan.getId(),
                carePlan.getPatientId(),
                request.taskType(),
                request.scheduledAt(),
                request.channel(),
                request.assignedTo()
        );
        return toFollowupTaskResponse(task);
    }

    @Transactional
    public PagedResultResponse<CarePlanApiDtos.Response.AlertEventResponse> listAlerts(
            Long tenantId,
            CarePlanApiDtos.Query.AlertEventPageQuery query,
            PageQueryRequest pageQuery) {
        clinicalAlertService.syncHighRiskAlerts(tenantId);
        CarePlanApiDtos.Query.AlertEventPageQuery q = query == null
                ? new CarePlanApiDtos.Query.AlertEventPageQuery(null, null, null, null, null)
                : query;
        List<AlertEvent> matching = alertEventRepository.listByTenantId(tenantId).stream()
                .filter(alert -> q.patientId() == null || Objects.equals(q.patientId(), alert.getPatientId()))
                .filter(alert -> q.carePlanId() == null || Objects.equals(q.carePlanId(), alert.getCarePlanId()))
                .filter(alert -> matches(alert.getSeverity(), q.severity()))
                .filter(alert -> matches(alert.getStatus(), q.status()))
                .filter(alert -> q.assignedTo() == null || Objects.equals(q.assignedTo(), alert.getAssignedTo()))
                .sorted(Comparator
                        .comparing(AlertEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AlertEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        Map<Long, Patient> patients = patientMap(tenantId);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream()
                        .map(alert -> toAlertResponse(tenantId, alert, patients.get(alert.getPatientId())))
                        .toList()
        );
    }

    @Transactional
    public CarePlanApiDtos.Response.AlertEventResponse resolveAlert(
            Long tenantId,
            CarePlanApiDtos.Request.ResolveAlertRequest request) {
        AlertEvent alert = alertEventRepository.findById(tenantId, request.alertId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alertId does not exist"));
        Long actionBy = request.actionBy() == null ? resolveCurrentUserId() : request.actionBy();
        String actionType = normalize(request.actionType(), "ACK");
        String targetStatus = switch (actionType) {
            case "RESOLVE" -> "RESOLVED";
            case "IGNORE" -> "IGNORED";
            default -> "ACKNOWLEDGED";
        };
        OffsetDateTime resolvedAt = Set.of("RESOLVED", "IGNORED").contains(targetStatus) ? now() : null;
        boolean updated = alertEventRepository.updateStatusIfCurrent(
                tenantId,
                alert.getId(),
                alert.getStatus(),
                targetStatus,
                resolvedAt,
                request.resolutionNote(),
                actionBy
        );
        if (!updated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "alert status has changed");
        }
        saveAlertAction(tenantId, alert.getId(), actionType, actionBy, request.actionNote());
        AlertEvent refreshed = alertEventRepository.findById(tenantId, alert.getId()).orElseThrow();
        return toAlertResponse(tenantId, refreshed, patientRepository.findById(tenantId, refreshed.getPatientId()).orElse(null));
    }

    @Transactional
    public CarePlanApiDtos.Response.CreateFollowupFromAlertResponse createFollowupFromAlert(
            Long tenantId,
            Long alertId,
            CarePlanApiDtos.Request.CreateFollowupFromAlertRequest request) {
        AlertEvent alert = alertEventRepository.findById(tenantId, alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alertId does not exist"));
        Patient patient = requirePatient(tenantId, alert.getPatientId());
        Long operatorId = resolveCurrentUserId();
        CarePlan plan = createPlan(
                tenantId,
                patient.getId(),
                defaultIfBlank(request.diseaseCode(), diseaseCodeOf(alert)),
                request.planType(),
                "DOCTOR_ASSIGNED",
                null,
                request.startDate(),
                request.endDate(),
                operatorId
        );
        FollowupTask task = createTask(
                tenantId,
                plan.getId(),
                patient.getId(),
                defaultIfBlank(request.taskType(), "CLINIC_VISIT"),
                request.scheduledAt(),
                defaultIfBlank(request.channel(), "APP"),
                operatorId
        );
        if (STATUS_OPEN.equalsIgnoreCase(alert.getStatus())) {
            boolean statusUpdated = alertEventRepository.updateStatusIfCurrent(
                    tenantId,
                    alert.getId(),
                    alert.getStatus(),
                    "ACKNOWLEDGED",
                    null,
                    "已建立随访计划：" + plan.getId(),
                    operatorId
            );
            if (!statusUpdated) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "alert status has changed");
            }
        }
        if (!alertEventRepository.linkCarePlan(tenantId, alert.getId(), plan.getId(), operatorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "failed to link alert with care plan");
        }
        saveAlertAction(
                tenantId,
                alert.getId(),
                "CREATE_FOLLOWUP",
                operatorId,
                defaultIfBlank(request.note(), "从预警建立随访计划")
        );
        AlertEvent refreshed = alertEventRepository.findById(tenantId, alert.getId()).orElseThrow();
        return new CarePlanApiDtos.Response.CreateFollowupFromAlertResponse(
                toCarePlanResponse(plan),
                toFollowupTaskResponse(task),
                toAlertResponse(tenantId, refreshed, patient)
        );
    }

    @Transactional(readOnly = true)
    public PagedResultResponse<CarePlanApiDtos.Response.PatientReportedDataResponse> listPatientReportedData(
            Long tenantId,
            CarePlanApiDtos.Query.PatientReportedDataPageQuery query,
            PageQueryRequest pageQuery) {
        CarePlanApiDtos.Query.PatientReportedDataPageQuery q = query == null
                ? new CarePlanApiDtos.Query.PatientReportedDataPageQuery(null, null, null, null, null)
                : query;
        List<PatientReportedData> candidates = q.carePlanId() != null
                ? patientReportedDataRepository.listByCarePlanId(tenantId, q.carePlanId())
                : q.patientId() != null
                ? patientReportedDataRepository.listByPatientId(tenantId, q.patientId())
                : patientReportedDataRepository.listByTenantId(tenantId);
        List<PatientReportedData> matching = candidates.stream()
                .filter(item -> q.patientId() == null || Objects.equals(q.patientId(), item.getPatientId()))
                .filter(item -> matches(item.getIndicatorCode(), q.indicatorCode()))
                .filter(item -> q.recordedFrom() == null
                        || item.getRecordedAt() != null && !item.getRecordedAt().isBefore(q.recordedFrom()))
                .filter(item -> q.recordedTo() == null
                        || item.getRecordedAt() != null && !item.getRecordedAt().isAfter(q.recordedTo()))
                .sorted(Comparator
                        .comparing(PatientReportedData::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PatientReportedData::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        Page page = page(pageQuery);
        return new PagedResultResponse<>(
                page.index(),
                page.size(),
                matching.size(),
                slice(matching, page).stream().map(this::toPatientDataResponse).toList()
        );
    }

    @Transactional
    public CarePlanApiDtos.Response.PatientReportedDataResponse reportPatientData(
            Long tenantId,
            CarePlanApiDtos.Request.ReportPatientDataRequest request) {
        Patient patient = requirePatient(tenantId, request.patientId());
        CarePlan plan = request.carePlanId() == null ? null
                : carePlanRepository.findById(tenantId, request.carePlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "carePlanId does not exist"));
        if (plan != null && !Objects.equals(plan.getPatientId(), patient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "carePlanId does not belong to patientId");
        }
        PatientReportedData data = new PatientReportedData();
        data.setTenantId(tenantId);
        data.setPatientId(patient.getId());
        data.setCarePlanId(plan == null ? null : plan.getId());
        data.setIndicatorCode(request.indicatorCode());
        data.setValueNumeric(request.valueNumeric());
        data.setValueText(trimToNull(request.valueText()));
        data.setSource(defaultIfBlank(request.source(), "PATIENT"));
        data.setDeviceInfo(request.deviceInfo());
        data.setRecordedAt(request.recordedAt() == null ? now() : request.recordedAt());
        data.setCreatedAt(now());
        return toPatientDataResponse(patientReportedDataRepository.save(data));
    }

    private CarePlan createPlan(Long tenantId,
                                Long patientId,
                                String diseaseCode,
                                String planType,
                                String enrollmentType,
                                Long templateId,
                                LocalDate startDate,
                                LocalDate endDate,
                                Long createdBy) {
        CarePlan plan = new CarePlan();
        plan.setTenantId(tenantId);
        plan.setPatientId(patientId);
        plan.setDiseaseCode(diseaseCode.trim());
        plan.setPlanType(normalize(planType, "STANDARD"));
        plan.setEnrollmentType(normalize(enrollmentType, "DOCTOR_ASSIGNED"));
        plan.setTemplateId(templateId);
        plan.setStatus(STATUS_ACTIVE);
        plan.setStartDate(startDate == null ? LocalDate.now(ZoneOffset.UTC) : startDate);
        plan.setEndDate(endDate);
        plan.setCreatedBy(createdBy);
        plan.setCreatedAt(now());
        return carePlanRepository.save(plan);
    }

    private FollowupTask createTask(Long tenantId,
                                    Long carePlanId,
                                    Long patientId,
                                    String taskType,
                                    OffsetDateTime scheduledAt,
                                    String channel,
                                    Long assignedTo) {
        FollowupTask task = new FollowupTask();
        task.setTenantId(tenantId);
        task.setCarePlanId(carePlanId);
        task.setPatientId(patientId);
        task.setTaskType(normalize(taskType, "CLINIC_VISIT"));
        task.setScheduledAt(scheduledAt == null ? now().plusDays(3) : scheduledAt);
        task.setChannel(normalize(channel, "APP"));
        task.setStatus(STATUS_PENDING);
        task.setAssignedTo(assignedTo == null ? resolveCurrentUserId() : assignedTo);
        task.setCreatedAt(now());
        return followupTaskRepository.save(task);
    }

    private void saveAlertAction(Long tenantId, Long alertId, String actionType, Long actionBy, String note) {
        AlertAction action = new AlertAction();
        action.setTenantId(tenantId);
        action.setAlertId(alertId);
        action.setActionType(normalize(actionType, "ACK"));
        action.setActionBy(actionBy);
        action.setActionNote(trimToNull(note));
        action.setCreatedAt(now());
        alertActionRepository.save(action);
    }

    private Patient requirePatient(Long tenantId, Long patientId) {
        return patientRepository.findById(tenantId, patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId does not exist"));
    }

    private Long resolveCurrentUserId() {
        UserSubject subject = currentUserSubjectProvider.requireCurrentSubject();
        return subject.userId();
    }

    private Map<Long, Patient> patientMap(Long tenantId) {
        return patientRepository.listByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Patient::getId, Function.identity(), (left, right) -> left));
    }

    private CarePlanApiDtos.Response.CarePlanResponse toCarePlanResponse(CarePlan plan) {
        return new CarePlanApiDtos.Response.CarePlanResponse(
                plan.getId(), plan.getPatientId(), plan.getDiseaseCode(), plan.getPlanType(),
                plan.getEnrollmentType(), plan.getTemplateId(), plan.getStatus(),
                plan.getStartDate(), plan.getEndDate(), plan.getCreatedBy(), plan.getCreatedAt()
        );
    }

    private CarePlanApiDtos.Response.FollowupTaskResponse toFollowupTaskResponse(FollowupTask task) {
        return new CarePlanApiDtos.Response.FollowupTaskResponse(
                task.getId(), task.getCarePlanId(), task.getPatientId(), task.getTaskType(),
                task.getScheduledAt(), task.getChannel(), task.getStatus(), task.getAssignedTo(),
                task.getCompletedAt(), task.getResultSummary(), task.getCreatedAt()
        );
    }

    private CarePlanApiDtos.Response.AlertEventResponse toAlertResponse(
            Long tenantId, AlertEvent alert, Patient patient) {
        List<CarePlanApiDtos.Shared.AlertActionItem> actions =
                alertActionRepository.listByAlertId(tenantId, alert.getId()).stream()
                        .map(action -> new CarePlanApiDtos.Shared.AlertActionItem(
                                action.getId(),
                                action.getAlertId(),
                                action.getActionType(),
                                action.getActionBy(),
                                action.getActionNote(),
                                action.getCreatedAt()))
                        .toList();
        return new CarePlanApiDtos.Response.AlertEventResponse(
                alert.getId(),
                alert.getPatientId(),
                patient == null ? null : patient.getPatientNo(),
                patient == null ? null : patient.getPatientName(),
                alert.getCarePlanId(),
                alert.getSourceSessionId(),
                alert.getSourceResultId(),
                alert.getTriggerType(),
                alert.getTriggerDetail(),
                alert.getSeverity(),
                alert.getStatus(),
                alert.getAssignedTo(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getResolutionNote(),
                actions
        );
    }

    private CarePlanApiDtos.Response.PatientReportedDataResponse toPatientDataResponse(PatientReportedData data) {
        return new CarePlanApiDtos.Response.PatientReportedDataResponse(
                data.getId(), data.getPatientId(), data.getCarePlanId(), data.getIndicatorCode(),
                data.getValueNumeric(), data.getValueText(), data.getSource(), data.getDeviceInfo(),
                data.getRecordedAt(), data.getCreatedAt()
        );
    }

    private boolean matches(String actual, String expected) {
        return expected == null || expected.isBlank() || actual != null && actual.equalsIgnoreCase(expected.trim());
    }

    private <T> List<T> slice(List<T> items, Page page) {
        long offset = (long) page.index() * page.size();
        if (offset >= items.size()) {
            return List.of();
        }
        int from = (int) offset;
        int to = Math.min(items.size(), from + page.size());
        return items.subList(from, to);
    }

    private Page page(PageQueryRequest pageQuery) {
        int index = pageQuery == null || pageQuery.page() == null
                ? DEFAULT_PAGE : Math.max(0, pageQuery.page());
        int size = pageQuery == null || pageQuery.size() == null
                ? DEFAULT_SIZE : Math.min(200, Math.max(1, pageQuery.size()));
        return new Page(index, size);
    }

    private String normalize(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized.toUpperCase(Locale.ROOT);
    }

    private String diseaseCodeOf(AlertEvent alert) {
        if (alert.getTriggerDetail() == null) {
            return "IMLD_RISK";
        }
        return defaultIfBlank(alert.getTriggerDetail().path("diseaseCode").asText(null), "IMLD_RISK");
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private record Page(int index, int size) {
    }
}
