package xenosoft.imldintelligence.module.identity.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.mybatis.PatientMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.mybatis.PatientPageRow;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 患者仓储实现类，基于 MyBatis-Plus 完成患者的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class PatientRepositoryImpl implements PatientRepository {
    private final PatientMapper patientMapper;

    @Override
    public Optional<Patient> findById(Long tenantId, Long patientId) {
        LambdaQueryWrapper<Patient> queryWrapper = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getTenantId, tenantId)
                .eq(Patient::getId, patientId);
        return Optional.ofNullable(patientMapper.selectOne(queryWrapper));
    }

    @Override
    public Optional<Patient> findByPatientNo(Long tenantId, String patientNo) {
        LambdaQueryWrapper<Patient> queryWrapper = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getTenantId, tenantId)
                .eq(Patient::getPatientNo, patientNo);
        return Optional.ofNullable(patientMapper.selectOne(queryWrapper));
    }

    @Override
    public List<Patient> listByTenantId(Long tenantId) {
        LambdaQueryWrapper<Patient> queryWrapper = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getTenantId, tenantId)
                .orderByDesc(Patient::getId);
        return patientMapper.selectList(queryWrapper);
    }

    @Override
    public long countByCondition(Long tenantId, String patientNo, String patientNameKeyword,
                                 String patientType, String status) {
        List<PatientPageRow> rows = patientMapper.listByConditionWithTotal(
                tenantId, patientNo, patientNameKeyword, patientType, status, 0, 1
        );
        if (rows.isEmpty()) {
            return 0L;
        }
        return Optional.ofNullable(rows.get(0).getTotalCount()).orElse(0L);
    }

    @Override
    public List<Patient> listByCondition(Long tenantId, String patientNo, String patientNameKeyword,
                                         String patientType, String status, long offset, int limit) {
        List<PatientPageRow> rows = patientMapper.listByConditionWithTotal(
                tenantId, patientNo, patientNameKeyword, patientType, status, offset, limit
        );
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(PatientPageRow::toPatient).toList();
    }

    @Override
    public Patient save(Patient patient) {
        patientMapper.insert(patient);
        return patient;
    }

    @Override
    public Patient update(Patient patient) {
        LambdaUpdateWrapper<Patient> updateWrapper = new LambdaUpdateWrapper<Patient>()
                .eq(Patient::getTenantId, patient.getTenantId())
                .eq(Patient::getId, patient.getId())
                .set(Patient::getPatientNo, patient.getPatientNo())
                .set(Patient::getPatientName, patient.getPatientName())
                .set(Patient::getGender, patient.getGender())
                .set(Patient::getBirthDate, patient.getBirthDate())
                .set(Patient::getIdNoEncrypted, patient.getIdNoEncrypted())
                .set(Patient::getMobileEncrypted, patient.getMobileEncrypted())
                .set(Patient::getOccupation, patient.getOccupation())
                .set(Patient::getCurrentAddress, patient.getCurrentAddress())
                .set(Patient::getNativePlace, patient.getNativePlace())
                .set(Patient::getConsanguinity, patient.getConsanguinity())
                .set(Patient::getPatientType, patient.getPatientType())
                .set(Patient::getStatus, patient.getStatus())
                .set(Patient::getSourceChannel, patient.getSourceChannel())
                .set(Patient::getUpdatedAt, OffsetDateTime.now());
        patientMapper.update(null, updateWrapper);
        return patient;
    }

    @Override
    public Boolean deleteById(Long tenantId, Long patientId) {
        LambdaUpdateWrapper<Patient> updateWrapper = new LambdaUpdateWrapper<Patient>()
                .eq(Patient::getTenantId, tenantId)
                .eq(Patient::getId, patientId)
                .set(Patient::getStatus, "INACTIVE")
                .set(Patient::getUpdatedAt, OffsetDateTime.now());
        return patientMapper.update(null, updateWrapper) > 0;
    }

    @Override
    public Boolean deleteByPatientNo(Long tenantId, String patientNo) {
        LambdaUpdateWrapper<Patient> updateWrapper = new LambdaUpdateWrapper<Patient>()
                .eq(Patient::getTenantId, tenantId)
                .eq(Patient::getPatientNo, patientNo)
                .set(Patient::getStatus, "INACTIVE")
                .set(Patient::getUpdatedAt, OffsetDateTime.now());
        return patientMapper.update(null, updateWrapper) > 0;
    }
}
