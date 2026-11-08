package xenosoft.imldintelligence.module.diagnoses.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xenosoft.imldintelligence.module.clinical.internal.model.ClinicalHistoryEntry;
import xenosoft.imldintelligence.module.clinical.internal.model.GeneticReport;
import xenosoft.imldintelligence.module.clinical.internal.model.LabResult;
import xenosoft.imldintelligence.module.clinical.internal.repository.ClinicalHistoryEntryRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticReportRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.GeneticVariantRepository;
import xenosoft.imldintelligence.module.clinical.internal.repository.LabResultRepository;
import xenosoft.imldintelligence.module.diagnoses.api.dto.ImldInferenceApiDtos;
import xenosoft.imldintelligence.module.identity.internal.model.Encounter;
import xenosoft.imldintelligence.module.identity.internal.model.Patient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * IMLD 诊断特征构建器。
 *
 * <p>负责把患者的检验、基因、病史、病理等原始数据转换为模型可识别的特征，
 * 并负责生成推理输入快照。该类只做纯计算与数据装配，不承担 HTTP 编排职责，
 * 便于单独进行单元测试和复用。</p>
 */
@Component
@RequiredArgsConstructor
public class ImldFeatureBuilder {

    private final LabResultRepository labResultRepository;
    private final ClinicalHistoryEntryRepository clinicalHistoryEntryRepository;
    private final GeneticReportRepository geneticReportRepository;
    private final GeneticVariantRepository geneticVariantRepository;
    private final ObjectMapper objectMapper;

    /**
     * 从最近的病理记录中提取派生来源快照。
     */
    public ObjectNode buildDerivedSourceSnapshot(Long tenantId, Patient patient, Encounter encounter) {
        ObjectNode derived = objectMapper.createObjectNode();
        findLatestPathologyEntry(tenantId, patient.getId(), encounter).ifPresent(entry -> {
            ObjectNode pathology = objectMapper.createObjectNode();
            JsonNode content = entry.getContentJson();
            if (content != null && content.get("reportText") != null && !content.get("reportText").isNull()) {
                pathology.put("reportText", content.get("reportText").asText());
            }
            if (content != null && content.get("nasScore") != null && !content.get("nasScore").isNull()) {
                pathology.put("nasScore", content.get("nasScore").asInt());
            }
            if (content != null && content.get("reportedAt") != null && !content.get("reportedAt").isNull()) {
                pathology.put("reportedAt", content.get("reportedAt").asText());
            }
            derived.set("pathology", pathology);
        });
        return derived;
    }

    /**
     * 构建 IMLD 模型推理请求。
     */
    public ImldInferenceApiDtos.Request.ImldPredictRequest buildInferenceRequest(
            Long tenantId, Patient patient, Encounter encounter, JsonNode input, ObjectNode derivedSourceSnapshot) {
        JsonNode n = input != null && input.has("inference_input") ? input.get("inference_input") : input;
        List<LabResult> labs = encounter != null ? labResultRepository.listByEncounterId(tenantId, encounter.getId())
                : labResultRepository.listByPatientId(tenantId, patient.getId());
        if (labs.isEmpty()) labs = labResultRepository.listByPatientId(tenantId, patient.getId());
        int age = clamp(intOf(n, "age").orElse(ageOf(patient)), 1, 120);
        int gender = clamp(intOf(n, "gender").orElse(genderFlag(patient.getGender())), 0, 1);
        double alt = Math.max(0D, doubleOf(n, "ALT", "alt").orElse(indicatorValue(labs, Set.of("ALT", "GPT")).orElse(45D)));
        double bilirubin = Math.max(0D, doubleOf(n, "bilirubin", "TBIL")
                .orElse(indicatorValue(labs, Set.of("TBIL", "BILIRUBIN")).orElse(21D)));
        double ceruloplasmin = Math.max(0D, doubleOf(n, "ceruloplasmin", "CP")
                .orElse(indicatorValue(labs, Set.of("CERULOPLASMIN", "CP")).orElse(180D)));
        int jaundice = clamp(intOf(n, "jaundice").orElse(0), 0, 1);
        Integer nasScore = intOf(n, "nasScore", "nas_score").orElseGet(() -> pathologyNasScore(derivedSourceSnapshot));
        List<ImldInferenceApiDtos.Request.GeneVariant> geneVariants = geneVariants(tenantId, patient.getId(), encounter, n);
        Map<String, Double> clinicalFeatures = buildClinicalFeatures(
                tenantId, patient, encounter, n, labs, derivedSourceSnapshot, age, gender, alt, bilirubin, ceruloplasmin, nasScore
        );
        return new ImldInferenceApiDtos.Request.ImldPredictRequest(
                age, gender, alt, bilirubin, ceruloplasmin, jaundice, nasScore, geneVariants,
                clinicalFeatures,
                patient.getPatientNo() == null ? String.valueOf(patient.getId()) : patient.getPatientNo());
    }

    /**
     * 生成诊断会话的输入快照，保留原始输入与派生来源。
     */
    public JsonNode buildSnapshot(JsonNode input,
                                  ImldInferenceApiDtos.Request.ImldPredictRequest inferenceRequest,
                                  ObjectNode derivedSourceSnapshot) {
        if (input != null && input.has("inference_input")) {
            if (input.isObject() && derivedSourceSnapshot != null && !derivedSourceSnapshot.isEmpty()) {
                ObjectNode snapshot = ((ObjectNode) input).deepCopy();
                ObjectNode sourceSnapshot = snapshot.has("source_snapshot") && snapshot.get("source_snapshot").isObject()
                        ? (ObjectNode) snapshot.get("source_snapshot")
                        : objectMapper.createObjectNode();
                mergeSourceSnapshot(sourceSnapshot, derivedSourceSnapshot);
                snapshot.set("source_snapshot", sourceSnapshot);
                return snapshot;
            }
            return input;
        }
        ObjectNode node = objectMapper.createObjectNode();
        ObjectNode sourceSnapshot = input != null && input.isObject() ? ((ObjectNode) input).deepCopy() : objectMapper.createObjectNode();
        mergeSourceSnapshot(sourceSnapshot, derivedSourceSnapshot);
        if (!sourceSnapshot.isEmpty()) {
            node.set("source_snapshot", sourceSnapshot);
        }
        node.set("inference_input", objectMapper.valueToTree(inferenceRequest));
        return node;
    }

    private Map<String, Double> buildClinicalFeatures(
            Long tenantId,
            Patient patient,
            Encounter encounter,
            JsonNode input,
            List<LabResult> labs,
            ObjectNode derivedSourceSnapshot,
            int age,
            int gender,
            double alt,
            double bilirubin,
            double ceruloplasmin,
            Integer nasScore) {
        Map<String, Double> features = new LinkedHashMap<>();
        putFeature(features, "age", (double) age);
        putFeature(features, "gender", (double) gender);
        putFeature(features, "ALT(U/L)", alt);
        putFeature(features, "TBIL(μmol/L)", bilirubin);
        putFeature(features, "ceruloplasmin", ceruloplasmin);
        if (nasScore != null) {
            putFeature(features, "NAS", nasScore.doubleValue());
        }

        copyClinicalFeaturesFromInput(features, input);
        copyClinicalFeaturesFromLabs(features, labs);
        JsonNode history = findLatestClinicalHistoryEntry(
                tenantId, patient.getId(), encounter, "PATIENT_HISTORY")
                .map(ClinicalHistoryEntry::getContentJson)
                .orElse(null);
        copyClinicalFeaturesFromHistory(features, history);
        Integer derivedNas = pathologyNasScore(derivedSourceSnapshot);
        if (derivedNas != null && !features.containsKey("NAS")) {
            putFeature(features, "NAS", derivedNas.doubleValue());
        }
        return features;
    }

    private void copyClinicalFeaturesFromInput(Map<String, Double> features, JsonNode input) {
        JsonNode clinicalFeatures = input != null && input.has("clinical_features") ? input.get("clinical_features") : null;
        if (clinicalFeatures == null || !clinicalFeatures.isObject()) {
            return;
        }
        clinicalFeatures.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isNumber()) {
                putFeature(features, entry.getKey(), value.asDouble());
            }
        });
    }

    private void copyClinicalFeaturesFromLabs(Map<String, Double> features, List<LabResult> labs) {
        putLabFeature(features, labs, "TBIL(μmol/L)", "TBIL", "BILIRUBIN");
        putLabFeature(features, labs, "DBIL(μmol/L)", "DBIL");
        putLabFeature(features, labs, "IBIL(μmol/L)", "IBIL");
        putLabFeature(features, labs, "ALT(U/L)", "ALT", "GPT");
        putLabFeature(features, labs, "AST(U/L)", "AST", "GOT");
        putLabFeature(features, labs, "TP(g/L)", "TP");
        putLabFeature(features, labs, "ALB(g/L)", "ALB");
        putLabFeature(features, labs, "GLB(g/L)", "GLB", "GLOB");
        putLabFeature(features, labs, "GLU(mmol/L)", "GLU");
        putLabFeature(features, labs, "URIC(μmol/L)", "URIC", "UA");
        putLabFeature(features, labs, "TG(mmol/L)", "TG");
        putLabFeature(features, labs, "CHOL(mmol/L)", "CHOL", "TC");
        putLabFeature(features, labs, "HDL-C(mmol/L)", "HDL_C", "HDL-C", "HDLC");
        putLabFeature(features, labs, "LDL-C(mmol/L)", "LDL_C", "LDL-C", "LDLC");
        putLabFeature(features, labs, "ALP(U/L)", "ALP");
        putLabFeature(features, labs, "GGT(U/L)", "GGT");
        putLabFeature(features, labs, "TBA(μmol/L)", "TBA");
        putLabFeature(features, labs, "NH3(μmol/L)", "NH3");
        putLabFeature(features, labs, "PLT(10^9/L)", "PLT");
        putLabFeature(features, labs, "WBC(10^9/L)", "WBC");
        putLabFeature(features, labs, "ceruloplasmin", "CERULOPLASMIN", "CER", "CP");
        putLabFeature(features, labs, "PIVKA（mAU/mL）", "PIVKA", "PIVKAII", "PIVKA_II");
        putLabFeature(features, labs, "PT(s)", "PT");
        putLabFeature(features, labs, "INR", "INR");
        putLabFeature(features, labs, "CRP(mg/L)", "CRP");
        putLabFeature(features, labs, "IgG(g/L)", "IGG");
        putLabFeature(features, labs, "IgA(g/L)", "IGA");
        putLabFeature(features, labs, "IgM(g/L)", "IGM");
    }

    private void copyClinicalFeaturesFromHistory(Map<String, Double> features, JsonNode history) {
        JsonNode diseaseHistory = history == null ? null : history.get("diseaseHistory");
        if (diseaseHistory == null || !diseaseHistory.isObject()) {
            return;
        }
        putFlagFeature(features, diseaseHistory, "Smoking", "smokingHistory");
        putFlagFeature(features, diseaseHistory, "Drinking", "drinkingHistory");
        putFlagFeature(features, diseaseHistory, "糖尿病病史", "diabetesHistory");
        putFlagFeature(features, diseaseHistory, "高血压病史", "hypertensionHistory");
        putFlagFeature(features, diseaseHistory, "高尿酸血症病史", "hyperuricemiaHistory");
        putFlagFeature(features, diseaseHistory, "高脂血症病史", "hyperlipidemiaHistory");
        putFlagFeature(features, diseaseHistory, "乙肝病史", "hepatitisBHistory");
    }

    private void putLabFeature(Map<String, Double> features, List<LabResult> labs, String feature, String... aliases) {
        indicatorValue(labs, Set.of(aliases)).ifPresent(value -> putFeature(features, feature, value));
    }

    private void putFlagFeature(Map<String, Double> features, JsonNode diseaseHistory, String feature, String key) {
        JsonNode value = diseaseHistory.get(key);
        if (value == null || value.isNull()) {
            return;
        }
        String normalized = value.asText("").trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || "UNKNOWN".equals(normalized)) {
            return;
        }
        boolean enabled = normalized.equals("YES")
                || normalized.equals("Y")
                || normalized.equals("TRUE")
                || normalized.equals("1");
        putFeature(features, feature, enabled ? 1D : 0D);
    }

    private void putFeature(Map<String, Double> features, String feature, Double value) {
        if (feature == null || feature.isBlank() || value == null || value.isNaN() || value.isInfinite()) {
            return;
        }
        features.put(feature, value);
    }

    private List<ImldInferenceApiDtos.Request.GeneVariant> geneVariants(Long tenantId, Long patientId, Encounter encounter, JsonNode n) {
        JsonNode array = n != null && n.has("gene_variants") ? n.get("gene_variants") : n != null ? n.get("geneVariants") : null;
        if (array != null && array.isArray()) {
            List<ImldInferenceApiDtos.Request.GeneVariant> variants = new ArrayList<>();
            for (JsonNode item : array) {
                variants.add(new ImldInferenceApiDtos.Request.GeneVariant(
                        strOf(item, "gene").orElse("UNKNOWN"),
                        strOf(item, "c_change", "cChange").orElse(null),
                        strOf(item, "p_change", "pChange").orElse(null),
                        strOf(item, "variant_type", "variantType").map(String::toLowerCase).orElse("unknown"),
                        strOf(item, "zygosity").map(String::toLowerCase).orElse("unknown"),
                        strOf(item, "pathogenicity", "classification").map(String::toLowerCase).orElse("unknown"),
                        doubleOf(item, "allele_frequency", "alleleFrequency").orElse(null)
                ));
            }
            return variants;
        }
        List<GeneticReport> reports = encounter != null ? geneticReportRepository.listByEncounterId(tenantId, encounter.getId())
                : geneticReportRepository.listByPatientId(tenantId, patientId);
        Optional<GeneticReport> latest = reports.stream().sorted(Comparator.comparing(GeneticReport::getCreatedAt).reversed()).findFirst();
        if (latest.isEmpty()) return List.of();
        return geneticVariantRepository.listByReportId(tenantId, latest.get().getId()).stream()
                .map(v -> new ImldInferenceApiDtos.Request.GeneVariant(
                        v.getGene(), v.getHgvsC(), v.getHgvsP(), normalize(v.getVariantType(), "unknown").toLowerCase(Locale.ROOT),
                        normalize(v.getZygosity(), "unknown").toLowerCase(Locale.ROOT),
                        normalize(v.getClassification(), "unknown").toLowerCase(Locale.ROOT), null
                )).toList();
    }

    private Optional<ClinicalHistoryEntry> findLatestPathologyEntry(Long tenantId, Long patientId, Encounter encounter) {
        return findLatestClinicalHistoryEntry(tenantId, patientId, encounter, "PATHOLOGY");
    }

    private Optional<ClinicalHistoryEntry> findLatestClinicalHistoryEntry(
            Long tenantId, Long patientId, Encounter encounter, String historyType) {
        List<ClinicalHistoryEntry> entries = encounter != null
                ? clinicalHistoryEntryRepository.listByEncounterId(tenantId, encounter.getId())
                : clinicalHistoryEntryRepository.listByPatientId(tenantId, patientId);
        return entries.stream()
                .filter(entry -> historyType.equalsIgnoreCase(entry.getHistoryType()))
                .sorted(Comparator.comparing((ClinicalHistoryEntry entry) ->
                                entry.getRecordedAt() != null ? entry.getRecordedAt() : now())
                        .reversed()
                        .thenComparing(ClinicalHistoryEntry::getId, Comparator.reverseOrder()))
                .findFirst();
    }

    private Integer pathologyNasScore(ObjectNode derivedSourceSnapshot) {
        if (derivedSourceSnapshot == null || derivedSourceSnapshot.get("pathology") == null) {
            return null;
        }
        JsonNode pathology = derivedSourceSnapshot.get("pathology");
        return pathology != null && pathology.get("nasScore") != null && !pathology.get("nasScore").isNull()
                ? pathology.get("nasScore").asInt()
                : null;
    }

    private void mergeSourceSnapshot(ObjectNode target, ObjectNode derived) {
        if (target == null || derived == null) {
            return;
        }
        target.setAll(derived);
    }

    private int ageOf(Patient patient) {
        LocalDate birth = patient.getBirthDate();
        return birth == null ? 40 : (int) ChronoUnit.YEARS.between(birth, LocalDate.now(ZoneOffset.UTC));
    }

    private int genderFlag(String gender) {
        String n = normalize(gender, "1").toLowerCase(Locale.ROOT);
        return n.equals("0") || n.equals("f") || n.equals("female") || n.equals("女") ? 0 : 1;
    }

    private Optional<Double> indicatorValue(List<LabResult> labs, Set<String> aliases) {
        Set<String> normalizedAliases = aliases.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        return labs.stream()
                .filter(l -> l.getIndicatorCode() != null && normalizedAliases.contains(l.getIndicatorCode().toUpperCase(Locale.ROOT)))
                .map(LabResult::getValueNumeric).filter(Objects::nonNull).findFirst();
    }

    private Optional<String> strOf(JsonNode node, String... keys) {
        if (node == null) return Optional.empty();
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return Optional.of(value.asText().trim());
        }
        return Optional.empty();
    }

    private Optional<Integer> intOf(JsonNode node, String... keys) {
        return strOf(node, keys).flatMap(value -> {
            try { return Optional.of(Integer.valueOf(value)); } catch (NumberFormatException ignored) { return Optional.empty(); }
        });
    }

    private Optional<Double> doubleOf(JsonNode node, String... keys) {
        return strOf(node, keys).flatMap(value -> {
            try { return Optional.of(Double.valueOf(value)); } catch (NumberFormatException ignored) { return Optional.empty(); }
        });
    }

    private String normalize(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
