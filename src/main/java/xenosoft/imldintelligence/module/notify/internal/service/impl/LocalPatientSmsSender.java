package xenosoft.imldintelligence.module.notify.internal.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xenosoft.imldintelligence.module.notify.internal.service.PatientSmsSender;

import java.util.UUID;

/**
 * 本地短信发送适配器。
 *
 * <p>用于开发联调，只记录脱敏号码并返回本地受理号。生产环境应替换为运营商适配器。</p>
 */
@Slf4j
@Component
public class LocalPatientSmsSender implements PatientSmsSender {

    @Override
    public SmsSendResult send(String mobile, String title, String content, String bizType, String bizId) {
        String providerMessageId = "LOCAL-" + UUID.randomUUID();
        log.info("Local patient SMS accepted (mobile={}, bizType={}, bizId={}, providerMessageId={})",
                maskMobile(mobile), bizType, bizId, providerMessageId);
        return new SmsSendResult("LOCAL_SMS", providerMessageId);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() <= 4) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
