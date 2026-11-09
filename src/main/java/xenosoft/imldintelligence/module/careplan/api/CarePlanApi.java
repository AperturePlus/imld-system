package xenosoft.imldintelligence.module.careplan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.careplan.api.dto.CarePlanApiDtos;

/**
 * 随访计划模块 HTTP 契约。
 *
 * <p>契约聚焦计划执行闭环，确保私有化场景在不出域时也能完成核心随访流程。</p>
 */
@Validated
@RequestMapping({"/api/v1/careplans", "/api/v1/app/careplans", "/api/v1/web/careplans"})
public interface CarePlanApi {

    /**
     * 分页查询随访计划。
     */
    @GetMapping
    ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.CarePlanResponse>> listCarePlans(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute CarePlanApiDtos.Query.CarePlanPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 创建随访计划。
     */
    @PostMapping
    ApiResponse<CarePlanApiDtos.Response.CarePlanResponse> createCarePlan(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody CarePlanApiDtos.Request.CreateCarePlanRequest request
    );

    /**
     * 分页查询随访任务。
     */
    @GetMapping("/tasks")
    ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.FollowupTaskResponse>> listFollowupTasks(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute CarePlanApiDtos.Query.FollowupTaskPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 调度随访任务。
     */
    @PostMapping("/tasks")
    ApiResponse<CarePlanApiDtos.Response.FollowupTaskResponse> scheduleFollowupTask(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody CarePlanApiDtos.Request.ScheduleFollowupTaskRequest request
    );

    /**
     * 分页查询告警事件。
     */
    @GetMapping("/alerts")
    ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.AlertEventResponse>> listAlertEvents(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute CarePlanApiDtos.Query.AlertEventPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 处理告警事件。
     */
    @PostMapping("/alerts/actions")
    ApiResponse<CarePlanApiDtos.Response.AlertEventResponse> resolveAlert(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody CarePlanApiDtos.Request.ResolveAlertRequest request
    );

    /**
     * 从一条预警直接建立随访计划和首个随访任务。
     */
    @PostMapping("/alerts/{alertId}/followup")
    ApiResponse<CarePlanApiDtos.Response.CreateFollowupFromAlertResponse> createFollowupFromAlert(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @PathVariable("alertId")
            @Positive(message = "alertId must be positive")
            Long alertId,
            @Valid @RequestBody CarePlanApiDtos.Request.CreateFollowupFromAlertRequest request
    );

    /**
     * 分页查询患者上报数据。
     */
    @GetMapping("/patient-data")
    ApiResponse<PagedResultResponse<CarePlanApiDtos.Response.PatientReportedDataResponse>> listPatientReportedData(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute CarePlanApiDtos.Query.PatientReportedDataPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 记录患者上报数据。
     */
    @PostMapping("/patient-data")
    ApiResponse<CarePlanApiDtos.Response.PatientReportedDataResponse> reportPatientData(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody CarePlanApiDtos.Request.ReportPatientDataRequest request
    );
}
