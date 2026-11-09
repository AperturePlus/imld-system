package xenosoft.imldintelligence.module.careplan.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xenosoft.imldintelligence.module.careplan.api.dto.CarePlanApiDtos;
import xenosoft.imldintelligence.module.careplan.api.service.ClinicalAlertService;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.model.CarePlan;
import xenosoft.imldintelligence.module.careplan.internal.model.FollowupTask;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertActionRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.CarePlanRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.FollowupTaskRepository;
import xenosoft.imldintelligence.module.careplan.internal.repository.PatientReportedDataRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.model.UserSubject;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.security.CurrentUserSubjectProvider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarePlanServiceTest {

    private CarePlanRepository carePlanRepository;
    private FollowupTaskRepository followupTaskRepository;
    private AlertEventRepository alertEventRepository;
    private AlertActionRepository alertActionRepository;
    private PatientRepository patientRepository;
    private CurrentUserSubjectProvider subjectProvider;
    private CarePlanService service;

    @BeforeEach
    void setUp() {
        carePlanRepository = mock(CarePlanRepository.class);
        followupTaskRepository = mock(FollowupTaskRepository.class);
        alertEventRepository = mock(AlertEventRepository.class);
        alertActionRepository = mock(AlertActionRepository.class);
        patientRepository = mock(PatientRepository.class);
        subjectProvider = mock(CurrentUserSubjectProvider.class);
        service = new CarePlanService(
                carePlanRepository,
                followupTaskRepository,
                alertEventRepository,
                alertActionRepository,
                mock(PatientReportedDataRepository.class),
                patientRepository,
                subjectProvider,
                mock(ClinicalAlertService.class)
        );
    }

    @Test
    void createFollowupFromAlertCreatesPlanTaskAndAlertAction() {
        Patient patient = new Patient();
        patient.setId(3L);
        patient.setTenantId(10L);
        patient.setPatientNo("P003");
        patient.setPatientName("测试患者");

        AlertEvent alert = new AlertEvent();
        alert.setId(7L);
        alert.setTenantId(10L);
        alert.setPatientId(3L);
        alert.setStatus("OPEN");
        alert.setSeverity("CRITICAL");
        alert.setTriggerType("DIAGNOSIS_HIGH_RISK");
        alert.setTriggerDetail(new ObjectMapper().createObjectNode()
                .put("diseaseName", "高风险肝病")
                .put("riskLevel", "HIGH"));
        alert.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).withNano(0));

        when(subjectProvider.requireCurrentSubject())
                .thenReturn(new UserSubject(100L, 10L, "DOCTOR", "ICU", Set.of("DOCTOR")));
        when(patientRepository.findById(10L, 3L)).thenReturn(Optional.of(patient));
        when(alertEventRepository.findById(10L, 7L))
                .thenReturn(Optional.of(alert));
        when(carePlanRepository.save(any(CarePlan.class))).thenAnswer(invocation -> {
            CarePlan plan = invocation.getArgument(0);
            plan.setId(20L);
            return plan;
        });
        when(followupTaskRepository.save(any(FollowupTask.class))).thenAnswer(invocation -> {
            FollowupTask task = invocation.getArgument(0);
            task.setId(30L);
            return task;
        });
        when(alertEventRepository.updateStatusIfCurrent(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
        when(alertEventRepository.linkCarePlan(10L, 7L, 20L, 100L)).thenReturn(true);
        when(alertActionRepository.listByAlertId(10L, 7L)).thenReturn(List.of());

        CarePlanApiDtos.Response.CreateFollowupFromAlertResponse response =
                service.createFollowupFromAlert(
                        10L,
                        7L,
                        new CarePlanApiDtos.Request.CreateFollowupFromAlertRequest(
                                "WILSON_DISEASE",
                                "STANDARD",
                                null,
                                null,
                                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1),
                                "CLINIC_VISIT",
                                "APP",
                                "从预警建立"
                        )
                );

        assertThat(response.carePlan().id()).isEqualTo(20L);
        assertThat(response.task().id()).isEqualTo(30L);
        assertThat(response.task().carePlanId()).isEqualTo(20L);
        assertThat(response.alert().id()).isEqualTo(7L);
        verify(alertEventRepository).linkCarePlan(10L, 7L, 20L, 100L);
        verify(alertActionRepository).save(any());
    }
}
