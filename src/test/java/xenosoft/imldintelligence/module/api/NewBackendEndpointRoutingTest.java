package xenosoft.imldintelligence.module.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xenosoft.imldintelligence.module.careplan.api.CarePlanController;
import xenosoft.imldintelligence.module.careplan.internal.service.CarePlanService;
import xenosoft.imldintelligence.module.notify.api.NotifyController;
import xenosoft.imldintelligence.module.notify.internal.service.NotificationApplicationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NewBackendEndpointRoutingTest {

    @Test
    void listAlertsRouteIsExposed() throws Exception {
        CarePlanService service = mock(CarePlanService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CarePlanController(service))
                .build();

        mockMvc.perform(get("/api/v1/web/careplans/alerts")
                        .header("X-Tenant-Id", "10"))
                .andExpect(status().isOk());

        verify(service).listAlerts(eq(10L), any(), any());
    }

    @Test
    void createFollowupFromAlertRouteIsExposed() throws Exception {
        CarePlanService service = mock(CarePlanService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new CarePlanController(service))
                .build();

        mockMvc.perform(post("/api/v1/web/careplans/alerts/7/followup")
                        .header("X-Tenant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diseaseCode": "WILSON_DISEASE",
                                  "planType": "STANDARD",
                                  "taskType": "CLINIC_VISIT",
                                  "channel": "APP"
                                }
                                """))
                .andExpect(status().isOk());

        verify(service).createFollowupFromAlert(eq(10L), eq(7L), any());
    }

    @Test
    void sendSmsRouteIsExposed() throws Exception {
        NotificationApplicationService service = mock(NotificationApplicationService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new NotifyController(service))
                .build();

        mockMvc.perform(post("/api/v1/web/notify/messages")
                        .header("X-Tenant-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bizType": "ALERT",
                                  "bizId": "7",
                                  "receiverType": "PATIENT",
                                  "receiverRefId": 3,
                                  "title": "预警提醒",
                                  "content": "请尽快复诊",
                                  "channel": "SMS"
                                }
                                """))
                .andExpect(status().isOk());

        verify(service).createMessage(eq(10L), any());
    }
}
