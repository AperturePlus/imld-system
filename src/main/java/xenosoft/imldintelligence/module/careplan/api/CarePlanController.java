package xenosoft.imldintelligence.module.careplan.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import xenosoft.imldintelligence.common.RequireAnyRole;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.audit.internal.aop.AuditedOperation;
import xenosoft.imldintelligence.module.audit.internal.aop.SensitiveAccessed;
import xenosoft.imldintelligence.module.careplan.api.dto.CarePlanApiDtos;
import xenosoft.imldintelligence.module.careplan.internal.service.CarePlanService;

/**
 * 随访计划、任务和预警处理控制器。
 */
@RestController
@RequiredArgsConstructor
public class CarePlanController implements CarePlanControllerContract {
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final CarePlanService carePlanService;

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.CarePlanResponse>> listCarePlans(
            Long tenantId,
            CarePlanApiDtos.Query.CarePlanPageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(carePlanService.listCarePlans(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "CREATE_CARE_PLAN", resourceType = "CARE_PLAN",
            resourceIdExpression = "#result.data.id()")
    public ApiResponse<CarePlanApiDtos.Response.CarePlanResponse> createCarePlan(
            Long tenantId,
            CarePlanApiDtos.Request.CreateCarePlanRequest request) {
        return ApiResponse.success(carePlanService.createCarePlan(tenantId, request));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.FollowupTaskResponse>> listFollowupTasks(
            Long tenantId,
            CarePlanApiDtos.Query.FollowupTaskPageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(carePlanService.listFollowupTasks(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "SCHEDULE_FOLLOWUP", resourceType = "FOLLOWUP_TASK",
            resourceIdExpression = "#result.data.id()")
    public ApiResponse<CarePlanApiDtos.Response.FollowupTaskResponse> scheduleFollowupTask(
            Long tenantId,
            CarePlanApiDtos.Request.ScheduleFollowupTaskRequest request) {
        return ApiResponse.success(carePlanService.scheduleFollowupTask(tenantId, request));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @SensitiveAccessed(
            sensitiveType = "ALERT_LIST",
            resourceType = "ALERT_EVENT",
            resourceIdExpression = "'all'",
            reasonExpression = "'view-high-risk-alerts'"
    )
    public ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.AlertEventResponse>> listAlertEvents(
            Long tenantId,
            CarePlanApiDtos.Query.AlertEventPageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(carePlanService.listAlerts(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "RESOLVE_ALERT", resourceType = "ALERT_EVENT",
            resourceIdExpression = "#p1.alertId()")
    public ApiResponse<CarePlanApiDtos.Response.AlertEventResponse> resolveAlert(
            Long tenantId,
            CarePlanApiDtos.Request.ResolveAlertRequest request) {
        return ApiResponse.success(carePlanService.resolveAlert(tenantId, request));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "CREATE_FOLLOWUP_FROM_ALERT", resourceType = "ALERT_EVENT",
            resourceIdExpression = "#p1")
    public ApiResponse<CarePlanApiDtos.Response.CreateFollowupFromAlertResponse> createFollowupFromAlert(
            Long tenantId,
            Long alertId,
            CarePlanApiDtos.Request.CreateFollowupFromAlertRequest request) {
        return ApiResponse.success(carePlanService.createFollowupFromAlert(tenantId, alertId, request));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.PatientReportedDataResponse>> listPatientReportedData(
            Long tenantId,
            CarePlanApiDtos.Query.PatientReportedDataPageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(carePlanService.listPatientReportedData(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "REPORT_PATIENT_DATA", resourceType = "PATIENT_REPORTED_DATA",
            resourceIdExpression = "#result.data.id()")
    public ApiResponse<CarePlanApiDtos.Response.PatientReportedDataResponse> reportPatientData(
            Long tenantId,
            CarePlanApiDtos.Request.ReportPatientDataRequest request) {
        return ApiResponse.success(carePlanService.reportPatientData(tenantId, request));
    }
}
