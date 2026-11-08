package xenosoft.imldintelligence.module.diagnoses.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xenosoft.imldintelligence.module.diagnoses.api.dto.DiagnosesApiDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisResult;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DiagnosisSession;
import xenosoft.imldintelligence.module.diagnoses.internal.model.DoctorFeedback;
import xenosoft.imldintelligence.module.diagnoses.internal.model.ModelRegistry;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisRecommendationRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DoctorFeedbackRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.ModelRegistryRepository;
import xenosoft.imldintelligence.module.identity.internal.model.UserAccount;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;
import xenosoft.imldintelligence.module.identity.internal.repository.UserAccountRepository;
import xenosoft.imldintelligence.module.identity.internal.security.CurrentUserSubjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosesCommandServiceDoctorAttributionTest {

    private static final Long TENANT_ID = 10L;
    private static final Long CURRENT_DOCTOR_ID = 100L;
    private static final Long OTHER_DOCTOR_ID = 200L;

    private DiagnosisSessionRepository sessionRepository;
    private DiagnosisResultRepository resultRepository;
    private DoctorFeedbackRepository feedbackRepository;
    private UserAccountRepository userAccountRepository;
    private ModelRegistryRepository modelRegistryRepository;
    private RecordingSubjectProvider subjectProvider;

    private DiagnosesCommandService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(DiagnosisSessionRepository.class);
        resultRepository = mock(DiagnosisResultRepository.class);
        feedbackRepository = mock(DoctorFeedbackRepository.class);
        @SuppressWarnings("unused")
        DiagnosisRecommendationRepository recommendationRepository = mock(DiagnosisRecommendationRepository.class);
        modelRegistryRepository = mock(ModelRegistryRepository.class);
        userAccountRepository = mock(UserAccountRepository.class);
        subjectProvider = new RecordingSubjectProvider();

        service = new DiagnosesCommandService(
                sessionRepository,
                resultRepository,
                recommendationRepository,
                feedbackRepository,
                modelRegistryRepository,
                userAccountRepository,
                new ObjectMapper(),
                subjectProvider
        );
    }

    @Test
    void submitFeedbackAttributesToCurrentUserWhenDoctorIdOmitted() {
        // Session recorded by nobody in particular — the fallback path is exercised.
        DiagnosisSession session = new DiagnosisSession();
        session.setId(1L);
        session.setTenantId(TENANT_ID);
        session.setDoctorId(null);
        when(sessionRepository.findById(TENANT_ID, 1L)).thenReturn(Optional.of(session));

        DiagnosisResult result = new DiagnosisResult();
        result.setId(7L);
        result.setSessionId(1L);
        when(resultRepository.findById(TENANT_ID, 7L)).thenReturn(Optional.of(result));

        // The tenant has another active doctor who sorts FIRST. The old code would
        // attribute the feedback to this arbitrary user, not the authenticated one.
        UserAccount otherDoctor = activeUser(OTHER_DOCTOR_ID);
        UserAccount currentDoctor = activeUser(CURRENT_DOCTOR_ID);
        when(userAccountRepository.listByTenantId(TENANT_ID))
                .thenReturn(List.of(otherDoctor, currentDoctor));

        subjectProvider.subject = Optional.of(
                new UserSubject(CURRENT_DOCTOR_ID, TENANT_ID, "DOCTOR", "ICU", Set.of("DOCTOR")));

        DiagnosesApiDtos.Request.SubmitDoctorFeedbackRequest request =
                new DiagnosesApiDtos.Request.SubmitDoctorFeedbackRequest(
                        1L, 7L, null, "ACCEPT", null, null);

        service.submitDoctorFeedback(TENANT_ID, request);

        ArgumentCaptor<DoctorFeedback> captor = ArgumentCaptor.forClass(DoctorFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getDoctorId())
                .as("feedback must be attributed to the authenticated doctor, not an arbitrary tenant user")
                .isEqualTo(CURRENT_DOCTOR_ID);
    }

    @Test
    void resolveModelUsesConflictSafeInsertAndReturnsPersistedModel() {
        ModelRegistry existing = new ModelRegistry();
        existing.setId(9L);
        existing.setTenantId(TENANT_ID);
        existing.setModelCode("IMLD_XGBOOST");
        existing.setModelVersion("v2_gene_clinical_fusion");
        existing.setStatus("ACTIVE");

        when(modelRegistryRepository.listByTenantId(TENANT_ID)).thenReturn(List.of());
        when(modelRegistryRepository.findByModelCodeAndModelVersion(
                TENANT_ID, "IMLD_XGBOOST", "v2_gene_clinical_fusion"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(modelRegistryRepository.saveIfAbsent(any(ModelRegistry.class))).thenReturn(false);

        ModelRegistry result = service.resolveModel(TENANT_ID, null);

        assertThat(result).isSameAs(existing);
        verify(modelRegistryRepository).saveIfAbsent(any(ModelRegistry.class));
    }

    private UserAccount activeUser(Long id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setStatus("ACTIVE");
        return user;
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
