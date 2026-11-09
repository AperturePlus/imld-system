package xenosoft.imldintelligence.module.notify.internal.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.service.SensitiveDataEncryptor;
import xenosoft.imldintelligence.module.notify.api.dto.NotifyApiDtos;
import xenosoft.imldintelligence.module.notify.internal.model.NotificationDelivery;
import xenosoft.imldintelligence.module.notify.internal.model.NotificationMessage;
import xenosoft.imldintelligence.module.notify.internal.repository.NotificationDeliveryRepository;
import xenosoft.imldintelligence.module.notify.internal.repository.NotificationMessageRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationApplicationServiceTest {

    @Test
    void smsMessageDecryptsPatientMobileAndRecordsDelivery() {
        NotificationMessageRepository messageRepository = mock(NotificationMessageRepository.class);
        NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
        PatientRepository patientRepository = mock(PatientRepository.class);
        SensitiveDataEncryptor encryptor = mock(SensitiveDataEncryptor.class);
        PatientSmsSender smsSender = mock(PatientSmsSender.class);
        NotificationApplicationService service = new NotificationApplicationService(
                messageRepository, deliveryRepository, patientRepository, encryptor, smsSender);

        Patient patient = new Patient();
        patient.setId(5L);
        patient.setTenantId(10L);
        patient.setMobileEncrypted("encrypted-mobile");
        when(patientRepository.findById(10L, 5L)).thenReturn(Optional.of(patient));
        when(encryptor.decrypt("encrypted-mobile")).thenReturn("13800138000");
        when(smsSender.send("13800138000", "预警提醒", "请尽快复诊", "ALERT", "7"))
                .thenReturn(new PatientSmsSender.SmsSendResult("LOCAL_SMS", "MSG-1"));
        when(messageRepository.save(any(NotificationMessage.class))).thenAnswer(invocation -> {
            NotificationMessage message = invocation.getArgument(0);
            message.setId(11L);
            return message;
        });
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenAnswer(invocation -> {
            NotificationDelivery delivery = invocation.getArgument(0);
            delivery.setId(12L);
            return delivery;
        });

        NotifyApiDtos.Response.NotificationMessageResponse response = service.createMessage(
                10L,
                new NotifyApiDtos.Request.CreateNotificationMessageRequest(
                        "ALERT",
                        "7",
                        "PATIENT",
                        5L,
                        "预警提醒",
                        "请尽快复诊",
                        "SMS",
                        null
                )
        );

        assertThat(response.id()).isEqualTo(11L);
        assertThat(response.status()).isEqualTo("SENT");
        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertThat(captor.getValue().getProviderName()).isEqualTo("LOCAL_SMS");
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo("ACCEPTED");
        verify(messageRepository).update(any(NotificationMessage.class));
    }
}
