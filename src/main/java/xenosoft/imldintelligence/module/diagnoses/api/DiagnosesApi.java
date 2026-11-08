package xenosoft.imldintelligence.module.diagnoses.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosisTraceDtos;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosesApiDtos;

/**
 * 诊断模块 HTTP 契约。
 *
 * <p>接口围绕会话触发、结果查看、人工反馈和模型管理设计，明确区分自动结论与人工校正。</p>
 */
@Validated
@RequestMapping({"/api/v1/diagnoses", "/api/v1/app/diagnoses", "/api/v1/web/diagnoses"})
public interface DiagnosesApi {

    /**
     * 分页查询诊断会话。
     */
    @GetMapping("/sessions")
    ApiResponse<PagedResultResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse>> listSessions(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute DiagnosesApiDtos.Query.SessionPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 查询单个诊断会话详情。
     */
    @GetMapping("/sessions/{sessionId}")
    ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> getSession(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @PathVariable("sessionId")
            @Positive(message = "sessionId must be positive")
            Long sessionId
    );

    /**
     * 启动诊断会话。
     */
    @PostMapping("/sessions")
    ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> startSession(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody DiagnosesApiDtos.Request.StartDiagnosisSessionRequest request
    );

    /**
     * 提交医生反馈。
     */
    @PostMapping("/feedbacks")
    ApiResponse<DiagnosesApiDtos.Response.DiagnosisSessionResponse> submitDoctorFeedback(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody DiagnosesApiDtos.Request.SubmitDoctorFeedbackRequest request
    );

    /**
     * 查询诊断决策链追溯信息。
     */
    @GetMapping("/sessions/{sessionId}/trace")
    ApiResponse<DiagnosisTraceDtos.Response.DiagnosisTraceResponse> getSessionTrace(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @PathVariable("sessionId")
            @Positive(message = "sessionId must be positive")
            Long sessionId
    );

    /**
     * 分页查询模型注册表。
     */
    @GetMapping("/models")
    ApiResponse<PagedResultResponse<DiagnosesApiDtos.Response.ModelRegistryResponse>> listModels(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @ModelAttribute DiagnosesApiDtos.Query.ModelRegistryPageQuery query,
            @Valid @ModelAttribute PageQueryRequest pageQuery
    );

    /**
     * 注册模型。
     */
    @PostMapping("/models")
    ApiResponse<DiagnosesApiDtos.Response.ModelRegistryResponse> registerModel(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody DiagnosesApiDtos.Request.RegisterModelRequest request
    );
}
