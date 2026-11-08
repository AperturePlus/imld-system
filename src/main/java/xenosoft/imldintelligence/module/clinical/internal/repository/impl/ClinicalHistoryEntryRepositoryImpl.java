package xenosoft.imldintelligence.module.clinical.internal.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import xenosoft.imldintelligence.module.clinical.internal.model.ClinicalHistoryEntry;
import xenosoft.imldintelligence.module.clinical.internal.repository.ClinicalHistoryEntryRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.mybatis.ClinicalHistoryEntryMapper;

import java.util.List;
import java.util.Optional;

/**
 * 临床病史条目仓储实现类，基于 MyBatis-Plus 完成临床病史条目的数据持久化。
 */
@Repository
@RequiredArgsConstructor
public class ClinicalHistoryEntryRepositoryImpl implements ClinicalHistoryEntryRepository {
    private final ClinicalHistoryEntryMapper clinicalHistoryEntryMapper;

    @Override
    public Optional<ClinicalHistoryEntry> findById(Long tenantId, Long id) {
        return Optional.ofNullable(clinicalHistoryEntryMapper.selectOne(new LambdaQueryWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                .eq(ClinicalHistoryEntry::getId, id)));
    }

    @Override
    public List<ClinicalHistoryEntry> listByTenantId(Long tenantId) {
        return clinicalHistoryEntryMapper.selectList(new LambdaQueryWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                .orderByDesc(ClinicalHistoryEntry::getId));
    }

    @Override
    public List<ClinicalHistoryEntry> listByPatientId(Long tenantId, Long patientId) {
        return clinicalHistoryEntryMapper.selectList(new LambdaQueryWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                .eq(ClinicalHistoryEntry::getPatientId, patientId)
                .orderByDesc(ClinicalHistoryEntry::getId));
    }

    @Override
    public List<ClinicalHistoryEntry> listByEncounterId(Long tenantId, Long encounterId) {
        return clinicalHistoryEntryMapper.selectList(new LambdaQueryWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                .eq(ClinicalHistoryEntry::getEncounterId, encounterId)
                .orderByDesc(ClinicalHistoryEntry::getId));
    }

    @Override
    public Optional<ClinicalHistoryEntry> findLatestByEncounterIdAndType(
            Long tenantId, Long encounterId, String historyType) {
        return Optional.ofNullable(clinicalHistoryEntryMapper.selectOne(
                new LambdaQueryWrapper<ClinicalHistoryEntry>()
                        .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                        .eq(ClinicalHistoryEntry::getEncounterId, encounterId)
                        .eq(ClinicalHistoryEntry::getHistoryType, historyType)
                        .orderByDesc(ClinicalHistoryEntry::getRecordedAt)
                        .orderByDesc(ClinicalHistoryEntry::getId)
                        .last("LIMIT 1")));
    }

    @Override
    public ClinicalHistoryEntry save(ClinicalHistoryEntry clinicalHistoryEntry) {
        clinicalHistoryEntryMapper.insert(clinicalHistoryEntry);
        return clinicalHistoryEntry;
    }

    @Override
    public ClinicalHistoryEntry update(ClinicalHistoryEntry clinicalHistoryEntry) {
        clinicalHistoryEntryMapper.update(clinicalHistoryEntry, new LambdaUpdateWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, clinicalHistoryEntry.getTenantId())
                .eq(ClinicalHistoryEntry::getId, clinicalHistoryEntry.getId()));
        return clinicalHistoryEntry;
    }

    @Override
    public Boolean deleteById(Long tenantId, Long id) {
        return clinicalHistoryEntryMapper.delete(new LambdaQueryWrapper<ClinicalHistoryEntry>()
                .eq(ClinicalHistoryEntry::getTenantId, tenantId)
                .eq(ClinicalHistoryEntry::getId, id)) > 0;
    }
}
