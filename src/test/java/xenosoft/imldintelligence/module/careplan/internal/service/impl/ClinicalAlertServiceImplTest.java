package xenosoft.imldintelligence.module.careplan.internal.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xenosoft.imldintelligence.module.careplan.internal.model.AlertEvent;
import xenosoft.imldintelligence.module.careplan.internal.repository.AlertEventRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisResultRepository;
import xenosoft.imldintelligence.module.diagnoses.internal.repository.DiagnosisSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ClinicalAlertServiceImplTest {

    @Test
    void highRiskDiagnosisCreatesAnIdempotentSourceAlert() {
        AlertEventRepository alertRepository = mock(AlertEventRepository.class);
        ClinicalAlertServiceImpl service = new ClinicalAlertServiceImpl(
                alertRepository,
                mock(DiagnosisSessionRepository.class),
                mock(DiagnosisResultRepository.class),
                new ObjectMapper()
        );

        service.recordDiagnosisAlert(
                10L, 3L, 20L, 30L, 40L,
                "WILSON_DISEASE", "肝豆状核变性", "HIGH", 0.91
        );

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertRepository).saveIfAbsent(captor.capture());
        AlertEvent event = captor.getValue();
        assertThat(event.getPatientId()).isEqualTo(3L);
        assertThat(event.getSourceSessionId()).isEqualTo(20L);
        assertThat(event.getSourceResultId()).isEqualTo(30L);
        assertThat(event.getSeverity()).isEqualTo("CRITICAL");
        assertThat(event.getStatus()).isEqualTo("OPEN");
        assertThat(event.getTriggerDetail().path("diseaseName").asText()).isEqualTo("肝豆状核变性");
    }

    @Test
    void lowRiskDiagnosisDoesNotCreateAlert() {
        AlertEventRepository alertRepository = mock(AlertEventRepository.class);
        ClinicalAlertServiceImpl service = new ClinicalAlertServiceImpl(
                alertRepository,
                mock(DiagnosisSessionRepository.class),
                mock(DiagnosisResultRepository.class),
                new ObjectMapper()
        );

        service.recordDiagnosisAlert(
                10L, 3L, 20L, 30L, 40L,
                "IMLD_RISK", "风险提示", "LOW", 0.12
        );

        verify(alertRepository, never()).saveIfAbsent(any());
    }
}
