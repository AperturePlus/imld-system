package xenosoft.imldintelligence.module.identity.internal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContext;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContextHolder;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;

import java.util.Objects;
import java.util.Optional;

/**
 * Enforces that the {@code X-Tenant-Id} request header matches the authenticated subject's tenant.
 *
 * <p>Only enforces when both an authenticated {@link UserSubject} and an {@code X-Tenant-Id} header
 * are present. Unauthenticated requests, or requests without the header, are left to the standard
 * authorization rules. A mismatch throws {@link AccessDeniedException}, handled by the existing
 * {@code JsonAccessDeniedHandler} as a 403 response.</p>
 */
@Component
@RequiredArgsConstructor
public class TenantHeaderInterceptor implements HandlerInterceptor {
    static final String TENANT_HEADER = "X-Tenant-Id";

    private final CurrentUserSubjectProvider currentUserSubjectProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Optional<UserSubject> current = currentUserSubjectProvider.getCurrentSubject();
        if (current.isEmpty()) {
            return true;
        }

        enrichAuditContext(current.get());

        String header = request.getHeader(TENANT_HEADER);
        if (header == null || header.isBlank()) {
            return true;
        }

        Long headerTenantId;
        try {
            headerTenantId = Long.valueOf(header.trim());
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("X-Tenant-Id header is invalid");
        }

        if (!Objects.equals(headerTenantId, current.get().tenantId())) {
            throw new AccessDeniedException("X-Tenant-Id header does not match authenticated tenant");
        }
        return true;
    }

    /**
     * 把认证用户补充到审计上下文，确保审计日志能够记录实际操作人。
     *
     * <p>审计上下文过滤器运行在 Spring Security 之前，只能读取请求头；
     * 认证完成后由本拦截器补齐用户标识与角色。</p>
     */
    private void enrichAuditContext(UserSubject subject) {
        AuditContext auditContext = AuditContextHolder.get();
        if (auditContext == null) {
            return;
        }
        // 身份以已验证的 JWT 主体为准，避免客户端伪造 X-User-Id / X-User-Role。
        auditContext.setTenantId(subject.tenantId());
        auditContext.setUserId(subject.userId());
        auditContext.setUserRole(firstNonBlank(
                subject.userType(),
                subject.roleCodes().stream().sorted().findFirst().orElse(null)));
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }
}
