package xenosoft.imldintelligence.module.careplan.api.service;

/**
 * 诊断高风险预警服务。
 */
public interface ClinicalAlertService {

    /**
     * 为高风险诊断结果生成预警。
     */
    void recordDiagnosisAlert(Long tenantId,
                              Long patientId,
                              Long sessionId,
                              Long resultId,
                              Long assignedTo,
                              String diseaseCode,
                              String diseaseName,
                              String riskLevel,
                              double confidence);

    /**
     * 为当前租户最近的高风险诊断结果补齐预警记录。
     */
    void syncHighRiskAlerts(Long tenantId);
}
