package xenosoft.imldintelligence.module.identity.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 身份与权限模块 DTO 分类目录。
 *
 * <p>按照查询、写入、响应、共享片段四类拆分，
 * 让患者身份、授权决策、知情同意等高敏数据在接口层就具备明确边界。</p>
 */
public final class IdentityApiDtos {
    private IdentityApiDtos() {
    }

    /**
     * 查询类 DTO。
     */
    public static final class Query {
        private Query() {
        }

        /**
         * 患者列表检索条件。
         */
        public record PatientPageQuery(
                String patientNo,
                String patientNameKeyword,
                String patientType,
                String status
        ) {
        }

        /**
         * 用户账号列表检索条件。
         */
        public record UserAccountPageQuery(
                String usernameKeyword,
                String userType,
                String deptName,
                String status
        ) {
        }

        /**
         * 知情同意记录检索条件。
         */
        public record ConsentRecordPageQuery(
                @Positive(message = "patientId must be positive")
                Long patientId,
                String consentType,
                String status
        ) {
        }
    }

    /**
     * 写入类 DTO。
     */
    public static final class Request {
        private Request() {
        }

        /**
         * 登录请求。
         */
        public record LoginCommand(
                @NotBlank(message = "username must not be blank")
                @Size(max = 100, message = "username must be at most 100 characters")
                String username,
                @NotBlank(message = "password must not be blank")
                @Size(max = 255, message = "password must be at most 255 characters")
                String password,
                @Size(max = 64, message = "tenantCode must be at most 64 characters")
                String tenantCode
        ) {
        }

        /**
         * 刷新令牌请求。
         */
        public record RefreshTokenCommand(
                @NotBlank(message = "refreshToken must not be blank")
                String refreshToken
        ) {
        }

        /**
         * 退出登录请求。
         */
        public record LogoutCommand(
                @NotBlank(message = "refreshToken must not be blank")
                String refreshToken
        ) {
        }

        /**
         * Send email verification code for user registration.
         */
        public record SendRegistrationEmailCodeCommand(
                @NotBlank(message = "username must not be blank")
                @Size(max = 100, message = "username must be at most 100 characters")
                String username,
                @NotBlank(message = "email must not be blank")
                @Size(max = 128, message = "email must be at most 128 characters")
                String email,
                @Size(max = 64, message = "tenantCode must be at most 64 characters")
                String tenantCode
        ) {
        }

        /**
         * Send email verification code for password recovery.
         */
        public record ForgotPasswordCommand(
                @NotBlank(message = "username must not be blank")
                @Size(max = 100, message = "username must be at most 100 characters")
                String username,
                @NotBlank(message = "email must not be blank")
                @Size(max = 128, message = "email must be at most 128 characters")
                String email,
                @Size(max = 64, message = "tenantCode must be at most 64 characters")
                String tenantCode
        ) {
        }

        /**
         * Register a user account with email verification code.
         */
        public record RegisterCommand(
                @NotBlank(message = "username must not be blank")
                @Size(max = 100, message = "username must be at most 100 characters")
                String username,
                @NotBlank(message = "password must not be blank")
                @Size(max = 255, message = "password must be at most 255 characters")
                String password,
                @NotBlank(message = "email must not be blank")
                @Size(max = 128, message = "email must be at most 128 characters")
                String email,
                @NotBlank(message = "emailCode must not be blank")
                @Size(max = 16, message = "emailCode must be at most 16 characters")
                String emailCode,
                @Size(max = 100, message = "displayName must be at most 100 characters")
                String displayName,
                @Size(max = 32, message = "userType must be at most 32 characters")
                String userType,
                @Size(max = 64, message = "tenantCode must be at most 64 characters")
                String tenantCode
        ) {
        }

        /**
         * Reset password using email verification code.
         */
        public record ResetPasswordCommand(
                @NotBlank(message = "username must not be blank")
                @Size(max = 100, message = "username must be at most 100 characters")
                String username,
                @NotBlank(message = "email must not be blank")
                @Size(max = 128, message = "email must be at most 128 characters")
                String email,
                @NotBlank(message = "newPassword must not be blank")
                @Size(max = 255, message = "newPassword must be at most 255 characters")
                String newPassword,
                @NotBlank(message = "emailCode must not be blank")
                @Size(max = 16, message = "emailCode must be at most 16 characters")
                String emailCode,
                @Size(max = 64, message = "tenantCode must be at most 64 characters")
                String tenantCode
        ) {
        }

        /**
         * Update the current authenticated account profile.
         */
        public record UpdateAccountProfileCommand(
                @Size(max = 100, message = "displayName must be at most 100 characters")
                String displayName,
                @Size(max = 100, message = "deptName must be at most 100 characters")
                String deptName,
                @Size(max = 128, message = "email must be at most 128 characters")
                String email,
                @Size(max = 32, message = "mobilePlaintext must be at most 32 characters")
                String mobilePlaintext,
                @Size(max = 255, message = "currentPassword must be at most 255 characters")
                String currentPassword
        ) {
        }

        /**
         * Change the current authenticated account password.
         */
        public record ChangePasswordCommand(
                @NotBlank(message = "currentPassword must not be blank")
                @Size(max = 255, message = "currentPassword must be at most 255 characters")
                String currentPassword,
                @NotBlank(message = "newPassword must not be blank")
                @Size(min = 6, max = 255, message = "newPassword must be between 6 and 255 characters")
                String newPassword,
                @NotBlank(message = "confirmPassword must not be blank")
                @Size(max = 255, message = "confirmPassword must be at most 255 characters")
                String confirmPassword,
                @NotBlank(message = "refreshToken must not be blank")
                String refreshToken
        ) {
        }

        /**
         * 新增或补录患者。
         *
         * <p>明文字段只用于边界层接收，后续实现必须完成加密与脱敏。</p>
         */
        public record CreatePatientRequest(
                @NotBlank(message = "patientNo must not be blank")
                @Size(max = 64, message = "patientNo must be at most 64 characters")
                String patientNo,
                @NotBlank(message = "patientName must not be blank")
                @Size(max = 100, message = "patientName must be at most 100 characters")
                String patientName,
                @Size(max = 16, message = "gender must be at most 16 characters")
                String gender,
                LocalDate birthDate,
                @Size(max = 64, message = "idNoPlaintext must be at most 64 characters")
                String idNoPlaintext,
                @Size(max = 32, message = "mobilePlaintext must be at most 32 characters")
                String mobilePlaintext,
                @NotBlank(message = "patientType must not be blank")
                @Size(max = 32, message = "patientType must be at most 32 characters")
                String patientType,
                @Size(max = 32, message = "sourceChannel must be at most 32 characters")
                String sourceChannel,
                @Size(max = 100, message = "occupation must be at most 100 characters")
                String occupation,
                @Size(max = 500, message = "currentAddress must be at most 500 characters")
                String currentAddress,
                @Size(max = 200, message = "nativePlace must be at most 200 characters")
                String nativePlace,
                Boolean consanguinity
        ) {
        }

        /**
         * 绑定患者外部标识。
         */
        public record BindExternalIdRequest(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @NotBlank(message = "idType must not be blank")
                @Size(max = 32, message = "idType must be at most 32 characters")
                String idType,
                @NotBlank(message = "idValue must not be blank")
                @Size(max = 128, message = "idValue must be at most 128 characters")
                String idValue,
                @Size(max = 128, message = "sourceOrg must be at most 128 characters")
                String sourceOrg,
                Boolean primary
        ) {
        }

        /**
         * 登记知情同意。
         */
        public record UpsertConsentRecordRequest(
                @Positive(message = "patientId must be positive")
                Long patientId,
                @Positive(message = "tocUserId must be positive")
                Long tocUserId,
                @NotBlank(message = "consentType must not be blank")
                @Size(max = 64, message = "consentType must be at most 64 characters")
                String consentType,
                @NotBlank(message = "consentVersion must not be blank")
                @Size(max = 64, message = "consentVersion must be at most 64 characters")
                String consentVersion,
                Boolean signedOffline,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                OffsetDateTime signedAt,
                Long fileId,
                @NotBlank(message = "status must not be blank")
                @Size(max = 32, message = "status must be at most 32 characters")
                String status,
                @Size(max = 500, message = "remark must be at most 500 characters")
                String remark
        ) {
        }

        /**
         * 角色授予请求。
         */
        public record GrantRoleRequest(
                @Positive(message = "userId must be positive")
                Long userId,
                @Positive(message = "roleId must be positive")
                Long roleId,
                @Positive(message = "grantedBy must be positive")
                Long grantedBy
        ) {
        }

        /**
         * 权限判定请求。
         */
        public record EvaluatePermissionRequest(
                @Positive(message = "userId must be positive")
                Long userId,
                @NotBlank(message = "resourceType must not be blank")
                @Size(max = 64, message = "resourceType must be at most 64 characters")
                String resourceType,
                @NotBlank(message = "resourceId must not be blank")
                @Size(max = 128, message = "resourceId must be at most 128 characters")
                String resourceId,
                @NotBlank(message = "action must not be blank")
                @Size(max = 64, message = "action must be at most 64 characters")
                String action,
                JsonNode subjectAttributes,
                JsonNode resourceAttributes
        ) {
        }
    }

    /**
     * 响应类 DTO。
     */
    public static final class Response {
        private Response() {
        }

        /**
         * 登录态响应。
         */
        public record AuthSessionResponse(
                String accessToken,
                String refreshToken,
                OffsetDateTime expiresAt,
                Shared.AuthenticatedUserItem user
        ) {
        }

        /**
         * Result of sending a verification code.
         */
        public record EmailCodeSendResponse(
                String email,
                String purpose,
                OffsetDateTime expiresAt,
                long resendAfterSeconds
        ) {
        }

        /**
         * Current authenticated account profile.
         */
        public record AccountProfileResponse(
                Long userId,
                Long tenantId,
                String userNo,
                String username,
                String displayName,
                String userType,
                String deptName,
                String email,
                String mobileMasked,
                List<String> roleCodes,
                OffsetDateTime lastLoginAt
        ) {
        }

        /**
         * 患者详情响应。
         */
        public record PatientDetailResponse(
                Long id,
                String patientNo,
                String patientName,
                String gender,
                LocalDate birthDate,
                String idNoMasked,
                String mobileMasked,
                String patientType,
                String status,
                String sourceChannel,
                List<Shared.ExternalIdItem> externalIds,
                List<Shared.GuardianItem> guardians,
                OffsetDateTime createdAt,
                OffsetDateTime updatedAt
        ) {
        }

        /**
         * 用户账号响应。
         */
        public record UserAccountResponse(
                Long id,
                String userNo,
                String username,
                String displayName,
                String userType,
                String deptName,
                String email,
                String status,
                OffsetDateTime lastLoginAt,
                List<Shared.RoleItem> roles
        ) {
        }

        /**
         * 知情同意响应。
         */
        public record ConsentRecordResponse(
                Long id,
                Long patientId,
                Long tocUserId,
                String consentType,
                String consentVersion,
                Boolean signedOffline,
                OffsetDateTime signedAt,
                Long fileId,
                String status,
                String remark,
                OffsetDateTime createdAt
        ) {
        }

        /**
         * 权限判定响应。
         */
        public record PermissionDecisionResponse(
                boolean allowed,
                String decisionSource,
                String matchedPolicyCode,
                String reason
        ) {
        }
    }

    /**
     * 共享片段 DTO。
     */
    public static final class Shared {
        private Shared() {
        }

        /**
         * 患者外部标识片段。
         */
        public record ExternalIdItem(
                Long id,
                String idType,
                String idValueMasked,
                String sourceOrg,
                Boolean primary
        ) {
        }

        /**
         * 监护人信息片段。
         */
        public record GuardianItem(
                Long id,
                String guardianName,
                String guardianMobileMasked,
                String guardianIdNoMasked,
                String relationType,
                Boolean primary,
                String status
        ) {
        }

        /**
         * 角色片段。
         */
        public record RoleItem(
                Long id,
                String roleCode,
                String roleName,
                String status
        ) {
        }

        /**
         * 已认证用户片段。
         */
        public record AuthenticatedUserItem(
                Long userId,
                Long tenantId,
                String username,
                String displayName,
                String userType,
                List<String> roleCodes
        ) {
        }
    }
}
