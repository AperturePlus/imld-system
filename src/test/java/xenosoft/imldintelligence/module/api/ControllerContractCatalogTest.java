package xenosoft.imldintelligence.module.api;

import org.junit.jupiter.api.Test;
import xenosoft.imldintelligence.module.careplan.api.CarePlanApi;
import xenosoft.imldintelligence.module.careplan.api.CarePlanController;
import xenosoft.imldintelligence.module.careplan.api.CarePlanControllerContract;
import xenosoft.imldintelligence.module.clinical.api.ClinicalApi;
import xenosoft.imldintelligence.module.clinical.api.ClinicalControllerContract;
import xenosoft.imldintelligence.module.diagnoses.api.DiagnosesApi;
import xenosoft.imldintelligence.module.diagnoses.api.DiagnosesControllerContract;
import xenosoft.imldintelligence.module.identity.api.IdentityApi;
import xenosoft.imldintelligence.module.identity.api.IdentityControllerContract;
import xenosoft.imldintelligence.module.integration.api.IntegrationApi;
import xenosoft.imldintelligence.module.integration.api.IntegrationControllerContract;
import xenosoft.imldintelligence.module.license.api.LicenseApi;
import xenosoft.imldintelligence.module.license.api.LicenseControllerContract;
import xenosoft.imldintelligence.module.notify.api.NotifyApi;
import xenosoft.imldintelligence.module.notify.api.NotifyController;
import xenosoft.imldintelligence.module.notify.api.NotifyControllerContract;
import xenosoft.imldintelligence.module.payment.api.PaymentApi;
import xenosoft.imldintelligence.module.payment.api.PaymentControllerContract;
import xenosoft.imldintelligence.module.report.api.ReportApi;
import xenosoft.imldintelligence.module.report.api.ReportControllerContract;
import xenosoft.imldintelligence.module.screening.api.ScreeningApi;
import xenosoft.imldintelligence.module.screening.api.ScreeningControllerContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 控制器契约目录测试。
 *
 * <p>所有未实现控制器都应先落到 controller contract 层，再由具体控制器类实现。</p>
 */
class ControllerContractCatalogTest {

    @Test
    void everyControllerContractShouldExtendItsHttpApiContract() {
        assertThat(CarePlanApi.class).isAssignableFrom(CarePlanControllerContract.class);
        assertThat(ClinicalApi.class).isAssignableFrom(ClinicalControllerContract.class);
        assertThat(DiagnosesApi.class).isAssignableFrom(DiagnosesControllerContract.class);
        assertThat(IdentityApi.class).isAssignableFrom(IdentityControllerContract.class);
        assertThat(IntegrationApi.class).isAssignableFrom(IntegrationControllerContract.class);
        assertThat(LicenseApi.class).isAssignableFrom(LicenseControllerContract.class);
        assertThat(NotifyApi.class).isAssignableFrom(NotifyControllerContract.class);
        assertThat(PaymentApi.class).isAssignableFrom(PaymentControllerContract.class);
        assertThat(ReportApi.class).isAssignableFrom(ReportControllerContract.class);
        assertThat(ScreeningApi.class).isAssignableFrom(ScreeningControllerContract.class);
    }

    @Test
    void carePlanAndNotifyContractsHaveConcreteControllers() {
        assertThat(CarePlanControllerContract.class).isAssignableFrom(CarePlanController.class);
        assertThat(NotifyControllerContract.class).isAssignableFrom(NotifyController.class);
    }
}
