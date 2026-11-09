package xenosoft.imldintelligence.module.notify.internal.service;

/**
 * 患者通知短信发送适配器。
 */
public interface PatientSmsSender {

    /**
     * 发送一条患者短信。
     */
    SmsSendResult send(String mobile, String title, String content, String bizType, String bizId);

    /**
     * 短信服务商返回结果。
     */
    record SmsSendResult(String providerName, String providerMessageId) {
    }
}
