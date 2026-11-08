package xenosoft.imldintelligence.module.clinical.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import xenosoft.imldintelligence.common.dto.ApiResponse;
import xenosoft.imldintelligence.module.clinical.api.dto.PatientRecordApiDtos;
import xenosoft.imldintelligence.module.clinical.api.dto.PatientRecordDetailDtos;

@Validated
@RequestMapping({"/api/v1/patient-records", "/api/v1/app/patient-records", "/api/v1/web/patient-records"})
public interface PatientRecordApi {

    @PostMapping({"", "/"})
    ApiResponse<PatientRecordApiDtos.Response.PatientRecordCreateResponse> createPatientRecord(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @Valid @RequestBody PatientRecordApiDtos.Request.CreatePatientRecordRequest request
    );

    /**
     * 按病号查询患者电子病历详情。
     *
     * @param tenantId 租户标识
     * @param patientNo 患者病号
     * @return 电子病历详情
     */
    @GetMapping("/{patientNo}")
    ApiResponse<PatientRecordDetailDtos.Response.PatientRecordDetailResponse> getPatientRecord(
            @RequestHeader("X-Tenant-Id")
            @Positive(message = "tenantId must be positive")
            Long tenantId,
            @PathVariable("patientNo")
            String patientNo
    );
}
