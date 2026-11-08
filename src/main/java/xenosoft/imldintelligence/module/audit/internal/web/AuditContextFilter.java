package xenosoft.imldintelligence.module.audit.internal.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContext;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContextHolder;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 审计上下文过滤器，负责从请求头提取审计上下文并绑定到当前线程。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnProperty(prefix = "imld.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditContextFilter extends OncePerRequestFilter {
    private static final Pattern SAFE_TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuditContext context = new AuditContext();
        context.setTenantId(parseLong(request.getHeader(AuditHeaderNames.TENANT_ID)));
        context.setUserId(parseLong(request.getHeader(AuditHeaderNames.USER_ID)));
        context.setUserRole(request.getHeader(AuditHeaderNames.USER_ROLE));
        context.setAppVersion(limit(request.getHeader(AuditHeaderNames.APP_VERSION), 64));
        context.setDeviceInfo(limit(request.getHeader(AuditHeaderNames.DEVICE_INFO), 255));
        context.setIpAddress(resolveIpAddress(request));

        String traceId = normalizeTraceId(request.getHeader(AuditHeaderNames.TRACE_ID));
        context.setTraceId(traceId);

        AuditContextHolder.set(context);
        MDC.put("traceId", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            AuditContextHolder.clear();
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeTraceId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = value.trim();
        return SAFE_TRACE_ID.matcher(trimmed).matches() ? trimmed : UUID.randomUUID().toString();
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(AuditHeaderNames.FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] segments = forwardedFor.split(",");
            if (segments.length > 0) {
                return limit(segments[0].trim(), 64);
            }
        }
        return limit(request.getRemoteAddr(), 64);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
