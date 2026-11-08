package xenosoft.imldintelligence.module.clinical.internal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import xenosoft.imldintelligence.module.clinical.api.dto.PatientRecordDetailDtos;
import xenosoft.imldintelligence.module.clinical.internal.model.ClinicalHistoryEntry;
import xenosoft.imldintelligence.module.clinical.internal.repository.ClinicalHistoryEntryRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticReportRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticVariantRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.ImagingReportRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.LabResultRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Encounter;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.EncounterRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.service.SensitiveDataEncryptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PatientRecordQueryServiceTest {

    @Test
    void returnsPersistedProfileAgeLatestNarrativeAndLaboratorySnapshot() {
        PatientRepository patientRepository = mock(PatientRepository.class);
        EncounterRepository encounterRepository = mock(EncounterRepository.class);
        ClinicalHistoryEntryRepository historyRepository = mock(ClinicalHistoryEntryRepository.class);
        LabResultRepository labResultRepository = mock(LabResultRepository.class);
        ImagingReportRepository imagingReportRepository = mock(ImagingReportRepository.class);
        GeneticReportRepository geneticReportRepository = mock(GeneticReportRepository.class);
        GeneticVariantRepository geneticVariantRepository = mock(GeneticVariantRepository.class);
        SensitiveDataEncryptor encryptor = mock(SensitiveDataEncryptor.class);
        ObjectMapper objectMapper = new ObjectMapper();
        PatientRecordQueryService service = new PatientRecordQueryService(
                patientRepository,
                encounterRepository,
                historyRepository,
                labResultRepository,
                imagingReportRepository,
                geneticReportRepository,
                geneticVariantRepository,
                encryptor,
                objectMapper
        );

        Patient patient = new Patient();
        patient.setId(1L);
        patient.setTenantId(10L);
        patient.setPatientNo("P-001");
        patient.setPatientName("测试患者");
        patient.setGender("男");
        patient.setBirthDate(LocalDate.of(1980, 1, 1));
        patient.setOccupation("教师");
        patient.setCurrentAddress("上海市");
        patient.setNativePlace("江苏");
        patient.setConsanguinity(Boolean.FALSE);

        Encounter encounter = new Encounter();
        encounter.setId(3L);
        encounter.setTenantId(10L);
        encounter.setPatientId(1L);
        encounter.setEncounterNo("VISIT-1");
        encounter.setStartAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        ClinicalHistoryEntry oldComplaint = entry("CHIEF_COMPLAINT", "旧主诉", 1L, encounter.getStartAt().minusDays(1));
        ClinicalHistoryEntry latestComplaint = entry("CHIEF_COMPLAINT", "新主诉", 2L, encounter.getStartAt());
        ObjectNode laboratory = objectMapper.createObjectNode();
        laboratory.putObject("clinicalBiochemistry").putObject("liverFunction").put("alt", 45.0);
        ClinicalHistoryEntry laboratorySnapshot = entry("LABORATORY_SCREENING", laboratory, 3L, encounter.getStartAt());

        when(patientRepository.findByPatientNo(10L, "P-001")).thenReturn(Optional.of(patient));
        when(encounterRepository.listByPatientId(10L, 1L)).thenReturn(List.of(encounter));
        when(historyRepository.listByEncounterId(10L, 3L))
                .thenReturn(List.of(latestComplaint, laboratorySnapshot, oldComplaint));
        when(labResultRepository.listByEncounterId(10L, 3L)).thenReturn(List.of());
        when(imagingReportRepository.listByEncounterId(10L, 3L)).thenReturn(List.of());
        when(geneticReportRepository.listByEncounterId(10L, 3L)).thenReturn(List.of());

        PatientRecordDetailDtos.Response.PatientRecordDetailResponse detail =
                service.getByPatientNo(10L, "P-001");

        assertThat(detail.age()).isEqualTo(46);
        assertThat(detail.occupation()).isEqualTo("教师");
        assertThat(detail.currentAddress()).isEqualTo("上海市");
        assertThat(detail.nativePlace()).isEqualTo("江苏");
        assertThat(detail.consanguinity()).isFalse();
        assertThat(detail.chiefComplaint()).isEqualTo("新主诉");
        assertThat(detail.laboratoryScreening().path("clinicalBiochemistry")
                .path("liverFunction").path("alt").asDouble()).isEqualTo(45.0);
    }

    private ClinicalHistoryEntry entry(String type, String text, Long id, OffsetDateTime recordedAt) {
        ObjectNode content = objectMapper().createObjectNode();
        content.put("text", text);
        return entry(type, content, id, recordedAt);
    }

    private ClinicalHistoryEntry entry(String type, ObjectNode content, Long id, OffsetDateTime recordedAt) {
        ClinicalHistoryEntry entry = new ClinicalHistoryEntry();
        entry.setId(id);
        entry.setTenantId(10L);
        entry.setPatientId(1L);
        entry.setEncounterId(3L);
        entry.setHistoryType(type);
        entry.setContentJson(content);
        entry.setRecordedAt(recordedAt);
        return entry;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
