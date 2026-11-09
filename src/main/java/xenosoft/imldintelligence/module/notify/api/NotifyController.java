package xenosoft.imldintelligence.module.notify.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import xenosoft.imldintelligence.common.RequireAnyRole;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.common.dto.PageQueryRequest;
import xenosoft.imldintelligence.common.dto.PagedResultResponse;
import xenosoft.imldintelligence.module.audit.internal.aop.AuditedOperation;
import xenosoft.imldintelligence.module.audit.internal.aop.SensitiveAccessed;
import xenosoft.imldintelligence.module.notify.api.dto.NotifyApiDtos;
import xenosoft.imldintelligence.module.notify.internal.service.NotificationApplicationService;

/**
 * 通知消息与送达记录控制器。
 */
@RestController
@RequiredArgsConstructor
public class NotifyController implements NotifyControllerContract {
    private static final String ROLE_DOCTOR = "DOCTOR";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final NotificationApplicationService notificationService;

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<NotifyApiDtos.Response.NotificationMessageResponse>> listMessages(
            Long tenantId,
            NotifyApiDtos.Query.NotificationMessagePageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(notificationService.listMessages(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    @AuditedOperation(action = "SEND_NOTIFICATION", resourceType = "NOTIFICATION_MESSAGE",
            resourceIdExpression = "#result.data.id()")
    @SensitiveAccessed(
            sensitiveType = "PATIENT_MOBILE",
            resourceType = "PATIENT",
            resourceIdExpression = "#p1.receiverRefId()",
            reasonExpression = "'send-alert-sms'"
    )
    public ApiResponse<NotifyApiDtos.Response.NotificationMessageResponse> createMessage(
            Long tenantId,
            NotifyApiDtos.Request.CreateNotificationMessageRequest request) {
        return ApiResponse.success(notificationService.createMessage(tenantId, request));
    }

    @Override
    @RequireAnyRole({ROLE_DOCTOR, ROLE_SYSTEM_ADMIN})
    public ApiResponse<PagedResultResponse<NotifyApiDtos.Response.NotificationDeliveryResponse>> listDeliveries(
            Long tenantId,
            NotifyApiDtos.Query.NotificationDeliveryPageQuery query,
            PageQueryRequest pageQuery) {
        return ApiResponse.success(notificationService.listDeliveries(tenantId, query, pageQuery));
    }

    @Override
    @RequireAnyRole({ROLE_SYSTEM_ADMIN})
    public ApiResponse<NotifyApiDtos.Response.NotificationDeliveryResponse> recordDeliveryCallback(
            Long tenantId,
            NotifyApiDtos.Request.RecordDeliveryCallbackRequest request) {
        return ApiResponse.success(notificationService.recordDeliveryCallback(tenantId, request));
    }
}
