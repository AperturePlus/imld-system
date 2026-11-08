package xenosoft.imldintelligence.module.audit.internal.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContext;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContextHolder;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class AuditContextFilterTest {
    private final AuditContextFilter filter = new AuditContextFilter();

    @AfterEach
    void cleanup() {
        AuditContextHolder.clear();
        MDC.clear();
    }

    @Test
    void shouldPopulateContextFromHeadersAndClearAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuditHeaderNames.TENANT_ID, "1001");
        request.addHeader(AuditHeaderNames.USER_ID, "2002");
        request.addHeader(AuditHeaderNames.USER_ROLE, "SYSTEM_ADMIN");
        request.addHeader(AuditHeaderNames.TRACE_ID, "trace-abc");
        request.addHeader(AuditHeaderNames.APP_VERSION, "1.2.3");
        request.addHeader(AuditHeaderNames.DEVICE_INFO, "Desktop Chrome");
        request.addHeader(AuditHeaderNames.FORWARDED_FOR, "10.0.0.8, 10.0.0.9");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                AuditContext context = AuditContextHolder.get();
                assertThat(context).isNotNull();
                assertThat(context.getTenantId()).isEqualTo(1001L);
                assertThat(context.getUserId()).isEqualTo(2002L);
                assertThat(context.getUserRole()).isEqualTo("SYSTEM_ADMIN");
                assertThat(context.getTraceId()).isEqualTo("trace-abc");
                assertThat(context.getIpAddress()).isEqualTo("10.0.0.8");
                assertThat(MDC.get("traceId")).isEqualTo("trace-abc");
            }
        });

        filter.doFilter(request, response, chain);

        assertThat(AuditContextHolder.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void shouldGenerateTraceIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuditHeaderNames.TENANT_ID, "1001");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                AuditContext context = AuditContextHolder.get();
                assertThat(context).isNotNull();
                assertThat(context.getTraceId()).isNotBlank();
                assertThat(context.getIpAddress()).isEqualTo("127.0.0.1");
                assertThat(MDC.get("traceId")).isEqualTo(context.getTraceId());
            }
        });

        filter.doFilter(request, response, chain);
    }

    @Test
    void shouldReplaceUnsafeTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AuditHeaderNames.TRACE_ID, "x".repeat(65));
        request.setRemoteAddr("127.0.0.1");

        MockFilterChain chain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) {
                AuditContext context = AuditContextHolder.get();
                assertThat(context).isNotNull();
                assertThat(context.getTraceId())
                        .isNotBlank()
                        .isNotEqualTo("x".repeat(65))
                        .hasSizeLessThanOrEqualTo(64);
            }
        });

        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }
}
