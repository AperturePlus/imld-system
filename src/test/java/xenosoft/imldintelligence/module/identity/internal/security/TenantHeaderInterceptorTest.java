package xenosoft.imldintelligence.module.identity.internal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContext;
import xenosoft.imldintelligence.module.audit.internal.context.AuditContextHolder;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantHeaderInterceptorTest {
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private RecordingSubjectProvider subjectProvider;
    private TenantHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        subjectProvider = new RecordingSubjectProvider();
        interceptor = new TenantHeaderInterceptor(subjectProvider);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        AuditContextHolder.clear();
    }

    @Test
    void proceedsWhenHeaderMatchesAuthenticatedTenant() {
        subjectProvider.subject = Optional.of(new UserSubject(88L, 66L, "DOCTOR", "Neurology", Set.of("DOCTOR")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TENANT_HEADER, "66");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsWhenHeaderDiffersFromAuthenticatedTenant() {
        subjectProvider.subject = Optional.of(new UserSubject(88L, 66L, "DOCTOR", "Neurology", Set.of("DOCTOR")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TENANT_HEADER, "77"); // tenant B
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("X-Tenant-Id header does not match authenticated tenant");
    }

    @Test
    void rejectsWhenHeaderIsNonNumeric() {
        subjectProvider.subject = Optional.of(new UserSubject(88L, 66L, "DOCTOR", "Neurology", Set.of("DOCTOR")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TENANT_HEADER, "not-a-number");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("X-Tenant-Id header is invalid");
    }

    @Test
    void proceedsWhenAuthenticatedButHeaderAbsent() {
        subjectProvider.subject = Optional.of(new UserSubject(88L, 66L, "DOCTOR", "Neurology", Set.of("DOCTOR")));

        MockHttpServletRequest request = new MockHttpServletRequest(); // no X-Tenant-Id
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    void proceedsWhenUnauthenticatedAndHeaderPresent() {
        subjectProvider.subject = Optional.empty();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TENANT_HEADER, "66");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    void proceedsWhenUnauthenticatedAndHeaderAbsent() {
        subjectProvider.subject = Optional.empty();

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    @Test
    void overwritesSpoofedAuditIdentityWithAuthenticatedSubject() {
        subjectProvider.subject = Optional.of(
                new UserSubject(88L, 66L, "DOCTOR", "Neurology", Set.of("DOCTOR")));
        AuditContext auditContext = new AuditContext();
        auditContext.setTenantId(77L);
        auditContext.setUserId(999L);
        auditContext.setUserRole("SYSTEM_ADMIN");
        AuditContextHolder.set(auditContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TENANT_HEADER, "66");
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-User-Role", "SYSTEM_ADMIN");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(auditContext.getTenantId()).isEqualTo(66L);
        assertThat(auditContext.getUserId()).isEqualTo(88L);
        assertThat(auditContext.getUserRole()).isEqualTo("DOCTOR");
    }

    /** Minimal stub of CurrentUserSubjectProvider so tests need no Spring context. */
    static class RecordingSubjectProvider extends CurrentUserSubjectProvider {
        Optional<UserSubject> subject = Optional.empty();

        @Override
        public Optional<UserSubject> getCurrentSubject() {
            return subject;
        }
    }
}
