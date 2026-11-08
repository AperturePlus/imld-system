package xenosoft.imldintelligence.module.clinical.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import xenosoft.imldintelligence.module.clinical.api.dto.PatientRecordDetailDtos;
import xenosoft.imldintelligence.module.clinical.internal.model.ClinicalHistoryEntry;
import xenosoft.imldintelligence.module.clinical.internal.model.GeneticReport;
import xenosoft.imldintelligence.module.clinical.internal.model.GeneticVariant;
import xenosoft.imldintelligence.module.clinical.internal.model.ImagingReport;
import xenosoft.imldintelligence.module.clinical.internal.model.LabResult;
import xenosoft.imldintelligence.module.clinical.internal.repository.ClinicalHistoryEntryRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticReportRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticVariantRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.ImagingReportRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.LabResultRepository;
import xenosoft.imldintelligence.module.identity.internal.model.Encounter;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;
import xenosoft.imldintelligence.module.identity.internal.repository.EncounterRepository;
import xenosoft.imldintelligence.module.identity.internal.repository.PatientRepository;
import xenosoft.imldintelligence.module.identity.internal.service.SensitiveDataEncryptor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.temporal.ChronoUnit;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 电子病历详情查询服务。
 *
 * <p>病历在归档时被分散存储到患者、就诊、检验、影像、基因以及病史条目等多张表中，
 * 本服务负责按患者病号定位最近一次就诊，并将各表数据聚合为一个完整的病历详情对象。</p>
 */
@Service
@RequiredArgsConstructor
public class PatientRecordQueryService {

    private static final String TYPE_CHIEF_COMPLAINT = "CHIEF_COMPLAINT";
    private static final String TYPE_PRESENT_ILLNESS = "PRESENT_ILLNESS";
    private static final String TYPE_HISTORY = "PATIENT_HISTORY";
    private static final String TYPE_PHYSICAL_EXAM = "PHYSICAL_EXAM";
    private static final String TYPE_LABORATORY_SCREENING = "LABORATORY_SCREENING";
    private static final String TYPE_PATHOLOGY = "PATHOLOGY";
    private static final String TYPE_CLINICAL_DECISION = "CLINICAL_DECISION";

    private static final String DEFAULT_SOURCE_TYPE = "MANUAL";

    private final PatientRepository patientRepository;
    private final EncounterRepository encounterRepository;
    private final ClinicalHistoryEntryRepository historyRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingReportRepository imagingReportRepository;
    private final GeneticReportRepository geneticReportRepository;
    private final GeneticVariantRepository geneticVariantRepository;
    private final SensitiveDataEncryptor sensitiveDataEncryptor;
    private final ObjectMapper objectMapper;

    /**
     * 按病号查询患者的电子病历详情。
     *
     * @param tenantId 租户标识
     * @param patientNo 患者病号
     * @return 病历详情，患者或就诊不存在时抛出 404
     */
    @Transactional(readOnly = true)
    public PatientRecordDetailDtos.Response.PatientRecordDetailResponse getByPatientNo(
            Long tenantId, String patientNo) {

        Patient patient = patientRepository.findByPatientNo(tenantId, patientNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该患者暂无电子病历"));

        Encounter encounter = encounterRepository.listByPatientId(tenantId, patient.getId()).stream()
                .max(Comparator
                        .comparing(Encounter::getStartAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Encounter::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "该患者暂无就诊记录"));

        Comparator<ClinicalHistoryEntry> entryOrder = Comparator
                .comparing(ClinicalHistoryEntry::getRecordedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(ClinicalHistoryEntry::getId, Comparator.nullsFirst(Comparator.naturalOrder()));
        Map<String, ClinicalHistoryEntry> entriesByType = historyRepository
                .listByEncounterId(tenantId, encounter.getId()).stream()
                .collect(Collectors.toMap(
                        ClinicalHistoryEntry::getHistoryType,
                        entry -> entry,
                        BinaryOperator.maxBy(entryOrder)));
        ClinicalHistoryEntry laboratorySnapshot = entriesByType.get(TYPE_LABORATORY_SCREENING);
        JsonNode laboratoryScreening = laboratorySnapshot == null
                ? rebuildLaboratoryScreening(labResultRepository.listByEncounterId(tenantId, encounter.getId()))
                : contentOf(laboratorySnapshot);

        return new PatientRecordDetailDtos.Response.PatientRecordDetailResponse(
                patient.getPatientNo(),
                patient.getPatientName(),
                patient.getGender(),
                ageAt(patient, encounter),
                toString(encounter.getStartAt()),
                decrypt(patient.getMobileEncrypted()),
                decrypt(patient.getIdNoEncrypted()),
                patient.getOccupation(),
                patient.getCurrentAddress(),
                patient.getNativePlace(),
                encounter.getDeptName(),
                encounter.getEncounterType(),
                patient.getConsanguinity(),
                textOf(entriesByType.get(TYPE_CHIEF_COMPLAINT)),
                textOf(entriesByType.get(TYPE_PRESENT_ILLNESS)),
                contentOf(entriesByType.get(TYPE_HISTORY)),
                contentOf(entriesByType.get(TYPE_PHYSICAL_EXAM)),
                laboratoryScreening,
                toImagingItems(imagingReportRepository.listByEncounterId(tenantId, encounter.getId())),
                toPathology(entriesByType.get(TYPE_PATHOLOGY)),
                toGenetic(tenantId, encounter.getId()),
                toDecision(entriesByType.get(TYPE_CLINICAL_DECISION)),
                encounter.getEncounterNo()
        );
    }

    /**
     * 把影像报告转换为响应条目。
     */
    private List<PatientRecordDetailDtos.Response.ImagingReportItem> toImagingItems(List<ImagingReport> reports) {
        return reports.stream()
                .map(report -> new PatientRecordDetailDtos.Response.ImagingReportItem(
                        report.getModality(),
                        report.getReportText(),
                        toString(report.getExaminedAt()),
                        report.getFileId() == null ? null : String.valueOf(report.getFileId()),
                        report.getSourceSystem()))
                .toList();
    }

    /**
     * 从病史条目中还原病理报告。
     *
     * <p>病理报告归档时以 {@code PATHOLOGY} 类型写入病史条目，而非独立表。</p>
     */
    private PatientRecordDetailDtos.Response.PathologyRecord toPathology(ClinicalHistoryEntry entry) {
        if (entry == null || entry.getContentJson() == null) {
            return new PatientRecordDetailDtos.Response.PathologyRecord(false, null, null, null, null, null);
        }
        JsonNode content = entry.getContentJson();
        return new PatientRecordDetailDtos.Response.PathologyRecord(
                true,
                textOrNull(content, "reportText"),
                intOrNull(content, "nasScore"),
                textOrNull(content, "reportedAt"),
                numberToString(content, "fileId"),
                textOrNull(content, "sourceType"));
    }

    /**
     * 聚合基因报告及其变异位点。
     */
    private PatientRecordDetailDtos.Response.GeneticSequencingRecord toGenetic(Long tenantId, Long encounterId) {
        GeneticReport report = geneticReportRepository.listByEncounterId(tenantId, encounterId).stream()
                .max(Comparator.comparing(GeneticReport::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (report == null) {
            return new PatientRecordDetailDtos.Response.GeneticSequencingRecord(
                    false, null, null, null, null, null, null, null, List.of());
        }

        List<GeneticVariant> variantEntities = geneticVariantRepository.listByReportId(tenantId, report.getId());
        String sourceType = variantEntities.stream()
                .map(GeneticVariant::getSourceType)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(DEFAULT_SOURCE_TYPE);

        List<PatientRecordDetailDtos.Response.GeneticVariantItem> variants = variantEntities.stream()
                .map(this::toVariant)
                .toList();

        return new PatientRecordDetailDtos.Response.GeneticSequencingRecord(
                true,
                report.getTestMethod(),
                report.getReportSource(),
                report.getReportDate() == null ? null : report.getReportDate().toString(),
                report.getSummary(),
                report.getConclusion(),
                report.getFileId() == null ? null : String.valueOf(report.getFileId()),
                sourceType,
                variants);
    }

    /**
     * 把基因变异实体转换为响应条目。
     */
    private PatientRecordDetailDtos.Response.GeneticVariantItem toVariant(GeneticVariant variant) {
        return new PatientRecordDetailDtos.Response.GeneticVariantItem(
                variant.getGene(),
                variant.getHgvsC(),
                variant.getHgvsP(),
                variant.getVariantType(),
                variant.getZygosity(),
                variant.getClassification(),
                variant.getEvidence());
    }

    /**
     * 从病史条目中还原临床决策。
     */
    private PatientRecordDetailDtos.Response.ClinicalDecisionRecord toDecision(ClinicalHistoryEntry entry) {
        if (entry == null || entry.getContentJson() == null) {
            return new PatientRecordDetailDtos.Response.ClinicalDecisionRecord(null, null);
        }
        JsonNode content = entry.getContentJson();
        return new PatientRecordDetailDtos.Response.ClinicalDecisionRecord(
                textOrNull(content, "diagnosis"),
                textOrNull(content, "treatmentPlan"));
    }

    /**
     * 还原检验筛查数据。
     *
     * <p>新归档会优先保存原始检验 JSON 快照；对于旧数据，则根据 {@code raw_data.sourcePath}
     * 重建嵌套结构，缺少路径时返回扁平结果，避免详情页完全丢失检验信息。</p>
     */
    private JsonNode rebuildLaboratoryScreening(List<LabResult> labResults) {
        ObjectNode rebuilt = objectMapper.createObjectNode();
        for (LabResult labResult : labResults) {
            JsonNode rawData = labResult.getRawData();
            String sourcePath = rawData == null ? null : rawData.path("sourcePath").asText(null);
            if (sourcePath == null || sourcePath.isBlank()) {
                continue;
            }
            JsonNode value = labResult.getValueNumeric() != null
                    ? DecimalNode.valueOf(java.math.BigDecimal.valueOf(labResult.getValueNumeric()))
                    : TextNode.valueOf(labResult.getValueText());
            putJsonPath(rebuilt, sourcePath, value);
        }
        if (!rebuilt.isEmpty()) {
            return rebuilt;
        }

        ArrayNode flatResults = objectMapper.createArrayNode();
        for (LabResult labResult : labResults) {
            ObjectNode item = flatResults.addObject();
            item.put("indicatorCode", labResult.getIndicatorCode());
            item.put("valueText", labResult.getValueText());
            if (labResult.getValueNumeric() != null) {
                item.put("valueNumeric", labResult.getValueNumeric());
            }
            if (labResult.getUnit() != null) {
                item.put("unit", labResult.getUnit());
            }
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        if (!flatResults.isEmpty()) {
            fallback.set("flatResults", flatResults);
        }
        return fallback;
    }

    private void putJsonPath(ObjectNode root, String path, JsonNode value) {
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            String segment = segments[index];
            JsonNode next = current.get(segment);
            if (next instanceof ObjectNode objectNode) {
                current = objectNode;
                continue;
            }
            ObjectNode child = objectMapper.createObjectNode();
            current.set(segment, child);
            current = child;
        }
        current.set(segments[segments.length - 1], value);
    }

    private Integer ageAt(Patient patient, Encounter encounter) {
        if (patient.getBirthDate() == null || encounter.getStartAt() == null) {
            return null;
        }
        long years = ChronoUnit.YEARS.between(patient.getBirthDate(), encounter.getStartAt().toLocalDate());
        return years < 0 ? null : (int) years;
    }

    private String textOf(ClinicalHistoryEntry entry) {
        if (entry == null || entry.getContentJson() == null) {
            return null;
        }
        return textOrNull(entry.getContentJson(), "text");
    }

    private JsonNode contentOf(ClinicalHistoryEntry entry) {
        return entry == null ? null : entry.getContentJson();
    }

    private String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return null;
        }
        try {
            return sensitiveDataEncryptor.decrypt(ciphertext);
        } catch (RuntimeException ex) {
            // 未配置加密密钥时解密不可用，降级为 null，避免整个详情接口失败
            return null;
        }
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private String numberToString(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
