package xenosoft.imldintelligence.module.identity.internal.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xenosoft.imldintelligence.module.identity.api.dto.IdentityApiDtos;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.model.PatientExternalId;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientExternalIdRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.service.SensitiveDataEncryptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplTest {

    @Mock private PatientRepository patientRepository;
    @Mock private PatientExternalIdRepository patientExternalIdRepository;
    @Mock private SensitiveDataEncryptor sensitiveDataEncryptor;

    private PatientServiceImpl patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientServiceImpl(
                patientRepository, patientExternalIdRepository, sensitiveDataEncryptor);
    }

    @Test
    void countPatientsForwardsToRepository() {
        var query = new IdentityApiDtos.Query.PatientPageQuery(null, "张", "INPATIENT", null);
        when(patientRepository.countByCondition(1L, null, "张", "INPATIENT", null)).thenReturn(42L);

        assertThat(patientService.countPatients(1L, query)).isEqualTo(42L);
    }

    @Test
    void listPatientsForwardsToRepository() {
        var query = new IdentityApiDtos.Query.PatientPageQuery(null, null, null, "ACTIVE");
        Patient p = new Patient();
        p.setId(1L);
        when(patientRepository.listByCondition(1L, null, null, null, "ACTIVE", 0L, 20))
                .thenReturn(List.of(p));

        List<Patient> result = patientService.listPatients(1L, query, 0L, 20);
        assertThat(result).hasSize(1);
    }

    @Test
    void createPatientEncryptsSensitiveFields() {
        when(sensitiveDataEncryptor.encrypt("13800138000")).thenReturn("encrypted_mobile");
        when(sensitiveDataEncryptor.encrypt("310101199001011234")).thenReturn("encrypted_id");
        when(patientRepository.save(any())).thenAnswer(invocation -> {
            Patient p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        var request = new IdentityApiDtos.Request.CreatePatientRequest(
                "P001", "张三", "MALE", LocalDate.of(1990, 1, 1),
                "310101199001011234", "13800138000", "INPATIENT", "HOSPITAL",
                "工程师", "上海市浦东新区", "上海", Boolean.FALSE);

        Patient result = patientService.createPatient(1L, request);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getPatientNo()).isEqualTo("P001");
        assertThat(result.getIdNoEncrypted()).isEqualTo("encrypted_id");
        assertThat(result.getMobileEncrypted()).isEqualTo("encrypted_mobile");
        assertThat(result.getOccupation()).isEqualTo("工程师");
        assertThat(result.getCurrentAddress()).isEqualTo("上海市浦东新区");
        assertThat(result.getNativePlace()).isEqualTo("上海");
        assertThat(result.getConsanguinity()).isFalse();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        verify(sensitiveDataEncryptor).encrypt("310101199001011234");
        verify(sensitiveDataEncryptor).encrypt("13800138000");
    }

    @Test
    void createPatientWithoutSensitiveFields() {
        when(patientRepository.save(any())).thenAnswer(invocation -> {
            Patient p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        var request = new IdentityApiDtos.Request.CreatePatientRequest(
                "P002", "李四", "FEMALE", null, null, null, "OUTPATIENT", null,
                null, null, null, null);

        Patient result = patientService.createPatient(1L, request);

        assertThat(result.getIdNoEncrypted()).isNull();
        assertThat(result.getMobileEncrypted()).isNull();
        assertThat(result.getSourceChannel()).isEqualTo("HOSPITAL");
    }

    @Test
    void updatePatientProfilePersistsDemographicsWithoutChangingPatientNo() {
        Patient patient = new Patient();
        patient.setId(102L);
        patient.setTenantId(1L);
        patient.setPatientNo("P102");
        patient.setPatientName("旧姓名");
        patient.setPatientType("OUTPATIENT");
        patient.setStatus("ACTIVE");
        patient.setSourceChannel("HOSPITAL");
        when(patientRepository.findById(1L, 102L)).thenReturn(Optional.of(patient));
        when(patientRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new IdentityApiDtos.Request.CreatePatientRequest(
                "P102", "新姓名", "FEMALE", LocalDate.of(1985, 2, 3),
                null, null, "OUTPATIENT", "HOSPITAL",
                "教师", "杭州市西湖区", "浙江", Boolean.TRUE);

        Patient result = patientService.updatePatientProfile(1L, 102L, request);

        assertThat(result.getPatientNo()).isEqualTo("P102");
        assertThat(result.getPatientName()).isEqualTo("新姓名");
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1985, 2, 3));
        assertThat(result.getOccupation()).isEqualTo("教师");
        assertThat(result.getCurrentAddress()).isEqualTo("杭州市西湖区");
        assertThat(result.getNativePlace()).isEqualTo("浙江");
        assertThat(result.getConsanguinity()).isTrue();
        verify(patientRepository).update(patient);
    }

    @Test
    void bindExternalIdSavesAndReturnsPatient() {
        Patient patient = new Patient();
        patient.setId(10L);
        patient.setTenantId(1L);
        when(patientRepository.findById(1L, 10L)).thenReturn(Optional.of(patient));
        when(patientExternalIdRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new IdentityApiDtos.Request.BindExternalIdRequest(
                10L, "MRN", "MRN-001", "HIS", true);

        Patient result = patientService.bindExternalId(1L, 10L, request);

        assertThat(result.getId()).isEqualTo(10L);

        ArgumentCaptor<PatientExternalId> captor = ArgumentCaptor.forClass(PatientExternalId.class);
        verify(patientExternalIdRepository).save(captor.capture());
        PatientExternalId saved = captor.getValue();
        assertThat(saved.getIdType()).isEqualTo("MRN");
        assertThat(saved.getIdValue()).isEqualTo("MRN-001");
        assertThat(saved.getIsPrimary()).isTrue();
    }

    @Test
    void bindExternalIdThrowsWhenPatientNotFound() {
        when(patientRepository.findById(1L, 999L)).thenReturn(Optional.empty());

        var request = new IdentityApiDtos.Request.BindExternalIdRequest(
                999L, "MRN", "V1", null, null);

        assertThatThrownBy(() -> patientService.bindExternalId(1L, 999L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Patient not found");
    }
}
