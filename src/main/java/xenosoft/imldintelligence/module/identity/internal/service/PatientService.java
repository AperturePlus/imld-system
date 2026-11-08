package xenosoft.imldintelligence.module.identity.internal.service;

import xenosoft.imldintelligence.module.identity.api.dto.IdentityApiDtos;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;

import java.util.List;

public interface PatientService {

    long countPatients(Long tenantId, IdentityApiDtos.Query.PatientPageQuery query);

    List<Patient> listPatients(Long tenantId, IdentityApiDtos.Query.PatientPageQuery query,
                               long offset, int limit);

    Patient createPatient(Long tenantId, IdentityApiDtos.Request.CreatePatientRequest request);

    Patient updatePatientProfile(Long tenantId, Long patientId,
                                 IdentityApiDtos.Request.CreatePatientRequest request);

    Patient bindExternalId(Long tenantId, Long patientId,
                           IdentityApiDtos.Request.BindExternalIdRequest request);
}
