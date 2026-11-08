package xenosoft.imldintelligence.module.diagnoses.internal.service.impl;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.util.FVec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import xenosoft.imldintelligence.module.diagnoses.api.dto.ImldInferenceApiDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.config.ImldInferenceProperties;
import xenosoft.imldintelligence.module.diagnoses.internal.service.ImldInferenceService;

import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "imld.inference.imld", name = "engine", havingValue = "xgboost-java", matchIfMissing = true)
public class ImldInferenceServiceImpl implements ImldInferenceService {
    private static final Logger logger = LoggerFactory.getLogger(ImldInferenceServiceImpl.class);

    private static final String DEFAULT_RISK_LEVEL_LOW = "低风险";
    private static final String DEFAULT_RISK_LEVEL_MEDIUM = "中风险";
    private static final String DEFAULT_RISK_LEVEL_HIGH = "高风险";

    private static final List<String> KEY_GENE_FEATURES = List.of(
            "ATP7B", "HFE", "SERPINA1", "ABCB11", "ABCB4", "JAG1", "NOTCH2", "SLC25A13",
            "CPS1", "OTC", "ASS1", "ASL", "FAH", "G6PC", "SLC37A4"
    );

    private static final List<String> DEFAULT_FEATURE_COLUMNS = List.of(
            "age", "gender", "ALT", "bilirubin", "ceruloplasmin", "jaundice",
            "gene_variant_count", "pathogenic_variant_count", "likely_pathogenic_variant_count",
            "gene_pathogenic_score_sum", "gene_high_impact_count", "gene_rare_variant_count",
            "gene_panel_hit_count", "max_single_variant_score"
    );

    private static final Map<String, Double> GENE_PANEL_WEIGHTS = Map.ofEntries(
            Map.entry("ATP7B", 1.00), Map.entry("HFE", 0.90), Map.entry("SERPINA1", 0.90),
            Map.entry("ABCB11", 0.80), Map.entry("ABCB4", 0.75), Map.entry("JAG1", 0.75),
            Map.entry("NOTCH2", 0.75), Map.entry("SLC25A13", 0.85), Map.entry("CPS1", 0.80),
            Map.entry("OTC", 0.80), Map.entry("ASS1", 0.80), Map.entry("ASL", 0.80),
            Map.entry("FAH", 0.90), Map.entry("G6PC", 0.80), Map.entry("SLC37A4", 0.80)
    );

    private static final Map<String, Double> VARIANT_TYPE_WEIGHTS = Map.ofEntries(
            Map.entry("missense", 0.70), Map.entry("nonsense", 1.00), Map.entry("frameshift", 1.00),
            Map.entry("splice", 0.95), Map.entry("deletion", 0.90), Map.entry("insertion", 0.85),
            Map.entry("cnv", 0.90), Map.entry("synonymous", 0.10), Map.entry("unknown", 0.40)
    );

    private static final Map<String, Double> ZYGOSITY_WEIGHTS = Map.ofEntries(
            Map.entry("heterozygous", 0.70), Map.entry("homozygous", 1.00),
            Map.entry("compound_heterozygous", 1.00), Map.entry("hemizygous", 0.95),
            Map.entry("unknown", 0.50)
    );

    private static final Set<String> HIGH_IMPACT_VARIANT_TYPES = Set.of("nonsense", "frameshift", "splice", "deletion", "cnv");
    private static final Set<String> PATHOGENIC_VARIANTS = Set.of("pathogenic", "likely_pathogenic");

    private static final double ALT_MIN = 0.0;
    private static final double ALT_MAX = 40.0;
    private static final double BILIRUBIN_MIN = 3.4;
    private static final double BILIRUBIN_MAX = 17.1;
    private static final double CERULOPLASMIN_MIN = 200.0;
    private static final double CERULOPLASMIN_MAX = 600.0;

    private final ImldInferenceProperties properties;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private final ReentrantLock modelLock = new ReentrantLock();
    private volatile Predictor predictor;
    private volatile String loadedModelLocation;
    private volatile ModelMeta modelMeta;

    public ImldInferenceServiceImpl(
            ImldInferenceProperties properties,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public ImldInferenceApiDtos.Response.HealthResponse health() {
        ModelMeta meta = loadModelMeta();
        boolean modelLoaded;
        try {
            ensurePredictor();
            modelLoaded = true;
        } catch (Exception ex) {
            modelLoaded = false;
            logger.warn("IMLD model is not available: {}", ex.getMessage());
        }
        return new ImldInferenceApiDtos.Response.HealthResponse(
                "running",
                modelLoaded,
                meta.version(),
                meta.metrics(),
                properties.getEngine()
        );
    }

    @Override
    public ImldInferenceApiDtos.Response.PredictData predict(ImldInferenceApiDtos.Request.ImldPredictRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ModelMeta meta = loadModelMeta();
        FeaturePreparation prepared = buildFeatureRow(request, meta.featureColumns());
        float[] aligned = alignFeatures(prepared.featureRow(), meta);

        double riskProbability = round4(predictProbability(aligned));
        int riskLabel = riskProbability >= 0.5 ? 1 : 0;
        String riskLevel = riskLevelFromProbability(riskProbability);

        List<ImldInferenceApiDtos.Response.FeatureContribution> contributions =
                topFeatureContributions(aligned, meta.featureColumns(), meta.featureMedians(), 8);
        List<String> suggestions = buildSuggestions(riskLevel, prepared.geneAbnormalities(), prepared.clinicalAbnormalities());

        return new ImldInferenceApiDtos.Response.PredictData(
                request.patientId(),
                riskProbability,
                riskLabel,
                riskLevel,
                properties.isDesensitizedClinicalEnabled(),
                prepared.dataHash(),
                prepared.clinicalAbnormalities(),
                prepared.geneAbnormalities(),
                contributions,
                suggestions,
                meta.metrics()
        );
    }

    @Override
    public ImldInferenceApiDtos.Response.BatchPredictData batchPredict(
            List<ImldInferenceApiDtos.Request.ImldPredictRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("batch payload must not be empty");
        }
        if (requests.size() > properties.getMaxBatchSize()) {
            throw new IllegalArgumentException("batch payload exceeds max size: " + properties.getMaxBatchSize());
        }

        ModelMeta meta = loadModelMeta();
        List<ImldInferenceApiDtos.Response.BatchPredictItem> results = new ArrayList<>(requests.size());
        int index = 1;
        for (ImldInferenceApiDtos.Request.ImldPredictRequest request : requests) {
            FeaturePreparation prepared = buildFeatureRow(request, meta.featureColumns());
            float[] aligned = alignFeatures(prepared.featureRow(), meta);
            double riskProbability = round4(predictProbability(aligned));
            results.add(new ImldInferenceApiDtos.Response.BatchPredictItem(
                    index++,
                    request.patientId(),
                    riskProbability,
                    riskProbability >= 0.5 ? 1 : 0,
                    riskLevelFromProbability(riskProbability),
                    prepared.clinicalAbnormalities().size(),
                    prepared.geneAbnormalities().size(),
                    prepared.dataHash()
            ));
        }

        return new ImldInferenceApiDtos.Response.BatchPredictData(
                requests.size(),
                results,
                properties.isDesensitizedClinicalEnabled(),
                meta.metrics()
        );
    }

    private FeaturePreparation buildFeatureRow(ImldInferenceApiDtos.Request.ImldPredictRequest payload,
                                               List<String> featureColumns) {
        List<NormalizedVariant> normalizedVariants = normalizeVariants(payload.geneVariants());
        boolean includeNasScore = featureColumns.contains("nasScore") || featureColumns.contains("NAS");
        long seed = stableSeed(payload, normalizedVariants, includeNasScore);
        SplittableRandom random = new SplittableRandom(seed);

        boolean useDesensitized = properties.isDesensitizedClinicalEnabled();
        double alt = useDesensitized
                ? numericalFuzzification(payload.ALT(), ALT_MIN, ALT_MAX, random)
                : payload.ALT();
        double bilirubin = useDesensitized
                ? numericalFuzzification(payload.bilirubin(), BILIRUBIN_MIN, BILIRUBIN_MAX, random)
                : payload.bilirubin();
        double ceruloplasmin = useDesensitized
                ? numericalFuzzification(payload.ceruloplasmin(), CERULOPLASMIN_MIN, CERULOPLASMIN_MAX, random)
                : payload.ceruloplasmin();

        Map<String, Object> desensitizedClinical = new LinkedHashMap<>();
        desensitizedClinical.put("age", payload.age());
        desensitizedClinical.put("gender", payload.gender());
        desensitizedClinical.put("ALT", alt);
        desensitizedClinical.put("bilirubin", bilirubin);
        desensitizedClinical.put("ceruloplasmin", ceruloplasmin);
        desensitizedClinical.put("jaundice", payload.jaundice());
        if (includeNasScore && payload.nasScore() != null) {
            desensitizedClinical.put("nasScore", payload.nasScore());
        }
        if (payload.clinicalFeatures() != null && !payload.clinicalFeatures().isEmpty()) {
            desensitizedClinical.put("clinical_features", payload.clinicalFeatures());
        }

        EncodedGeneFeatures encodedGene = encodeGeneVariants(normalizedVariants);

        Map<String, Double> row = new LinkedHashMap<>();
        putFeatureWithAliases(row, "age", payload.age().doubleValue());
        putFeatureWithAliases(row, "gender", payload.gender().doubleValue());
        putFeatureWithAliases(row, "ALT", alt);
        putFeatureWithAliases(row, "bilirubin", bilirubin);
        putFeatureWithAliases(row, "ceruloplasmin", ceruloplasmin);
        putFeatureWithAliases(row, "jaundice", payload.jaundice().doubleValue());
        if (includeNasScore && payload.nasScore() != null) {
            putFeatureWithAliases(row, "nasScore", payload.nasScore().doubleValue());
        }
        putClinicalFeatures(row, payload.clinicalFeatures());
        row.putAll(encodedGene.features());

        for (String feature : defaultInferenceFeatureColumns()) {
            row.putIfAbsent(feature, 0.0);
        }
        if (includeNasScore) {
            row.putIfAbsent("nasScore", payload.nasScore() == null ? 0.0 : payload.nasScore().doubleValue());
        }

        List<Map<String, Object>> geneVariantsForHash = normalizedVariants.stream()
                .map(this::toHashVariant)
                .toList();
        Map<String, Object> hashPayload = new LinkedHashMap<>();
        hashPayload.put("clinical", desensitizedClinical);
        hashPayload.put("gene_variants", geneVariantsForHash);
        String dataHash = sha256OfObject(hashPayload);

        return new FeaturePreparation(
                row,
                dataHash,
                analyzeClinicalAbnormalities(payload),
                encodedGene.abnormalVariants()
        );
    }

    private void putClinicalFeatures(Map<String, Double> row, Map<String, Double> clinicalFeatures) {
        if (clinicalFeatures == null || clinicalFeatures.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Double> entry : clinicalFeatures.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null
                    || entry.getValue().isNaN() || entry.getValue().isInfinite()) {
                continue;
            }
            putFeatureWithAliases(row, entry.getKey(), entry.getValue());
        }
    }

    private void putFeatureWithAliases(Map<String, Double> row, String feature, double value) {
        row.put(feature, value);
        for (String alias : featureAliases(feature)) {
            row.put(alias, value);
        }
    }

    private List<String> featureAliases(String feature) {
        String normalized = normalizeFeatureName(feature);
        return switch (normalized) {
            case "age" -> List.of("age");
            case "gender" -> List.of("gender");
            case "smoking", "smokinghistory" -> List.of("Smoking");
            case "drinking", "drinkinghistory" -> List.of("Drinking");
            case "diabetes", "diabeteshistory" -> List.of("糖尿病病史");
            case "hypertension", "hypertensionhistory" -> List.of("高血压病史");
            case "hyperuricemia", "hyperuricemiahistory" -> List.of("高尿酸血症病史");
            case "hyperlipidemia", "hyperlipidemiahistory" -> List.of("高脂血症病史");
            case "hepatitisb", "hepatitisbhistory", "hbv" -> List.of("乙肝病史");
            case "nas", "nasscore" -> List.of("NAS", "nasScore");
            case "ste", "stekpa" -> List.of("STE（Kpa）");
            case "usss" -> List.of("USSS");
            case "alt", "altul", "gpt" -> List.of("ALT", "ALT(U/L)");
            case "bilirubin", "tbil", "tbilumoll" -> List.of("bilirubin", "TBIL(μmol/L)");
            case "dbil", "dbilumoll" -> List.of("DBIL(μmol/L)");
            case "ibil", "ibilumoll" -> List.of("IBIL(μmol/L)");
            case "ast", "astul", "got" -> List.of("AST(U/L)");
            case "tp", "tpgl" -> List.of("TP(g/L)");
            case "alb", "albgl" -> List.of("ALB(g/L)");
            case "glb", "glob", "glbgl" -> List.of("GLB(g/L)");
            case "glu", "glummoll" -> List.of("GLU(mmol/L)");
            case "uric", "ua", "uricumoll" -> List.of("URIC(μmol/L)");
            case "tg", "tgmmoll" -> List.of("TG(mmol/L)");
            case "chol", "tc", "cholmmoll" -> List.of("CHOL(mmol/L)");
            case "hdlc", "hdlcmmoll" -> List.of("HDL-C(mmol/L)");
            case "ldlc", "ldlcmmoll" -> List.of("LDL-C(mmol/L)");
            case "alp", "alpul" -> List.of("ALP(U/L)");
            case "ggt", "ggtul" -> List.of("GGT(U/L)");
            case "tba", "tbaumoll" -> List.of("TBA(μmol/L)");
            case "nh3", "nh3umoll" -> List.of("NH3(μmol/L)");
            case "plt", "plt109l" -> List.of("PLT(10^9/L)");
            case "wbc", "wbc109l" -> List.of("WBC(10^9/L)");
            case "cer", "cp", "ceruloplasmin" -> List.of("ceruloplasmin", "CERULOPLASMIN");
            case "pivka", "pivkamauml" -> List.of("PIVKA（mAU/mL）");
            case "pt", "pts" -> List.of("PT(s)");
            case "inr" -> List.of("INR");
            case "crp", "crpmgl" -> List.of("CRP(mg/L)");
            case "igg", "igggl" -> List.of("IgG(g/L)");
            case "iga", "igagl" -> List.of("IgA(g/L)");
            case "igm", "igmgl" -> List.of("IgM(g/L)");
            default -> List.of();
        };
    }

    private Map<String, Object> toHashVariant(NormalizedVariant variant) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("gene", variant.gene());
        value.put("c_change", variant.cChange());
        value.put("p_change", variant.pChange());
        value.put("variant_type", variant.variantType());
        value.put("zygosity", variant.zygosity());
        value.put("pathogenicity", variant.pathogenicity());
        value.put("allele_frequency", variant.alleleFrequency());
        return value;
    }

    private List<ImldInferenceApiDtos.Response.ClinicalAbnormality> analyzeClinicalAbnormalities(
            ImldInferenceApiDtos.Request.ImldPredictRequest payload) {
        List<ImldInferenceApiDtos.Response.ClinicalAbnormality> abnormalities = new ArrayList<>();

        if (payload.ALT() > ALT_MAX) {
            String severity = payload.ALT() >= ALT_MAX * 2 ? "高" : "中";
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "ALT",
                    round4(payload.ALT()),
                    List.of(ALT_MIN, ALT_MAX),
                    "high",
                    severity
            ));
        }

        if (payload.bilirubin() > BILIRUBIN_MAX) {
            String severity = payload.bilirubin() >= BILIRUBIN_MAX * 2 ? "高" : "中";
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "bilirubin",
                    round4(payload.bilirubin()),
                    List.of(BILIRUBIN_MIN, BILIRUBIN_MAX),
                    "high",
                    severity
            ));
        } else if (payload.bilirubin() < BILIRUBIN_MIN) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "bilirubin",
                    round4(payload.bilirubin()),
                    List.of(BILIRUBIN_MIN, BILIRUBIN_MAX),
                    "low",
                    "中"
            ));
        }

        if (payload.ceruloplasmin() < CERULOPLASMIN_MIN) {
            String severity = payload.ceruloplasmin() <= CERULOPLASMIN_MIN * 0.5 ? "高" : "中";
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "ceruloplasmin",
                    round4(payload.ceruloplasmin()),
                    List.of(CERULOPLASMIN_MIN, CERULOPLASMIN_MAX),
                    "low",
                    severity
            ));
        } else if (payload.ceruloplasmin() > CERULOPLASMIN_MAX) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "ceruloplasmin",
                    round4(payload.ceruloplasmin()),
                    List.of(CERULOPLASMIN_MIN, CERULOPLASMIN_MAX),
                    "high",
                    "中"
            ));
        }

        if (payload.jaundice() == 1) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "jaundice",
                    1.0,
                    List.of(0.0, 0.0),
                    "positive",
                    "中"
            ));
        }
        return abnormalities;
    }

    private EncodedGeneFeatures encodeGeneVariants(List<NormalizedVariant> variants) {
        Map<String, Double> features = new LinkedHashMap<>();
        features.put("gene_variant_count", 0.0);
        features.put("pathogenic_variant_count", 0.0);
        features.put("likely_pathogenic_variant_count", 0.0);
        features.put("gene_pathogenic_score_sum", 0.0);
        features.put("gene_high_impact_count", 0.0);
        features.put("gene_rare_variant_count", 0.0);
        features.put("gene_panel_hit_count", 0.0);
        features.put("max_single_variant_score", 0.0);
        for (String gene : KEY_GENE_FEATURES) {
            features.put(gene + "_variant_count", 0.0);
            features.put(gene + "_pathogenic_score_sum", 0.0);
        }

        LinkedHashSet<String> hitGenes = new LinkedHashSet<>();
        List<ImldInferenceApiDtos.Response.GeneAbnormality> abnormalVariants = new ArrayList<>();

        for (NormalizedVariant variant : variants) {
            double score = singleVariantRiskScore(variant);

            features.put("gene_variant_count", features.get("gene_variant_count") + 1);
            features.put("gene_pathogenic_score_sum", features.get("gene_pathogenic_score_sum") + score);
            features.put("max_single_variant_score", Math.max(features.get("max_single_variant_score"), score));

            if (GENE_PANEL_WEIGHTS.containsKey(variant.gene())) {
                hitGenes.add(variant.gene());
            }
            if ("pathogenic".equals(variant.pathogenicity())) {
                features.put("pathogenic_variant_count", features.get("pathogenic_variant_count") + 1);
            }
            if ("likely_pathogenic".equals(variant.pathogenicity())) {
                features.put("likely_pathogenic_variant_count", features.get("likely_pathogenic_variant_count") + 1);
            }
            if (HIGH_IMPACT_VARIANT_TYPES.contains(variant.variantType())) {
                features.put("gene_high_impact_count", features.get("gene_high_impact_count") + 1);
            }
            if (variant.alleleFrequency() != null && variant.alleleFrequency() < 0.01) {
                features.put("gene_rare_variant_count", features.get("gene_rare_variant_count") + 1);
            }
            if (KEY_GENE_FEATURES.contains(variant.gene())) {
                String countKey = variant.gene() + "_variant_count";
                String scoreKey = variant.gene() + "_pathogenic_score_sum";
                features.put(countKey, features.get(countKey) + 1);
                features.put(scoreKey, features.get(scoreKey) + score);
            }

            if (score >= 0.60 || PATHOGENIC_VARIANTS.contains(variant.pathogenicity())) {
                abnormalVariants.add(new ImldInferenceApiDtos.Response.GeneAbnormality(
                        variant.gene(),
                        variant.cChange(),
                        variant.pChange(),
                        variant.variantType(),
                        variant.zygosity(),
                        variant.pathogenicity(),
                        variant.alleleFrequency(),
                        round4(score),
                        buildVariantReason(variant, score)
                ));
            }
        }

        features.put("gene_panel_hit_count", (double) hitGenes.size());
        features.put("gene_pathogenic_score_sum", round6(features.get("gene_pathogenic_score_sum")));
        features.put("max_single_variant_score", round6(features.get("max_single_variant_score")));

        abnormalVariants = abnormalVariants.stream()
                .sorted(Comparator.comparingDouble(ImldInferenceApiDtos.Response.GeneAbnormality::variantRiskScore).reversed())
                .collect(Collectors.toList());
        return new EncodedGeneFeatures(features, abnormalVariants);
    }

    private String buildVariantReason(NormalizedVariant variant, double score) {
        List<String> reasons = new ArrayList<>();
        if (GENE_PANEL_WEIGHTS.containsKey(variant.gene())) {
            reasons.add(variant.gene() + " 属于 IMLD 重点基因");
        }
        if (PATHOGENIC_VARIANTS.contains(variant.pathogenicity())) {
            reasons.add("临床注释为 " + variant.pathogenicity());
        }
        if (HIGH_IMPACT_VARIANT_TYPES.contains(variant.variantType())) {
            reasons.add(variant.variantType() + " 属于高影响变异");
        }
        if (Set.of("homozygous", "compound_heterozygous", "hemizygous").contains(variant.zygosity())) {
            reasons.add("合子型为 " + variant.zygosity());
        }
        if (variant.alleleFrequency() != null && variant.alleleFrequency() < 0.01) {
            reasons.add("群体频率较低");
        }
        if (reasons.isEmpty()) {
            reasons.add("综合评分偏高");
        }
        return String.join("；", reasons) + "（评分=" + String.format(Locale.ROOT, "%.4f", score) + "）";
    }

    private List<String> buildSuggestions(
            String riskLevel,
            List<ImldInferenceApiDtos.Response.GeneAbnormality> abnormalVariants,
            List<ImldInferenceApiDtos.Response.ClinicalAbnormality> clinicalAbnormalities) {
        List<String> suggestions = new ArrayList<>();
        switch (riskLevel) {
            case DEFAULT_RISK_LEVEL_HIGH -> {
                suggestions.add("建议尽快前往具备遗传代谢性肝病诊疗能力的专科医院进一步检查。");
                suggestions.add("建议结合肝功能复查、铜代谢相关检查，必要时进行目标基因 panel/WES 复核。");
            }
            case DEFAULT_RISK_LEVEL_MEDIUM -> {
                suggestions.add("建议结合家族史、临床症状和复查指标开展进一步评估。");
                suggestions.add("若存在可疑位点，建议转诊临床遗传科或肝病专科。");
            }
            default -> {
                suggestions.add("当前模型评估为低风险，但不能替代临床诊断。");
                suggestions.add("若存在家族史或症状进展，仍建议线下随访。");
            }
        }

        if (!abnormalVariants.isEmpty()) {
            suggestions.add("当前最可疑异常位点来自 " + abnormalVariants.get(0).gene()
                    + "，建议优先复核该基因位点注释并结合家系验证。");
        }
        if (clinicalAbnormalities.stream().anyMatch(item ->
                "ceruloplasmin".equals(item.feature()) && "low".equals(item.direction()))) {
            suggestions.add("铜蓝蛋白偏低时，可结合 24h 尿铜、角膜 K-F 环等检查综合判断。");
        }
        return suggestions.stream().limit(5).toList();
    }

    private Predictor ensurePredictor() {
        String modelLocation = resolveResourceLocation(properties.getModelFilePath());
        Predictor local = predictor;
        if (local != null && modelLocation.equals(loadedModelLocation)) {
            return local;
        }

        modelLock.lock();
        try {
            if (predictor != null && modelLocation.equals(loadedModelLocation)) {
                return predictor;
            }
            Resource modelResource = resourceLoader.getResource(Objects.requireNonNull(modelLocation, "modelLocation"));
            if (!modelResource.exists()) {
                throw new IllegalStateException("IMLD model file not found: " + modelLocation);
            }
            try (InputStream in = modelResource.getInputStream()) {
                Predictor loaded = new Predictor(in);
                predictor = loaded;
                loadedModelLocation = modelLocation;
                return loaded;
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to load IMLD model file: " + modelLocation, ex);
            }
        } finally {
            modelLock.unlock();
        }
    }

    private ModelMeta loadModelMeta() {
        ModelMeta local = modelMeta;
        if (local != null) {
            return local;
        }
        String metadataLocation = resolveResourceLocation(properties.getMetadataFilePath());
        Resource metadataResource = resourceLoader.getResource(Objects.requireNonNull(metadataLocation, "metadataLocation"));
        if (!metadataResource.exists()) {
            ModelMeta fallback = new ModelMeta(defaultInferenceFeatureColumns(), Map.of(), Map.of(), properties.getModelVersionFallback());
            modelMeta = fallback;
            return fallback;
        }
        try {
            JsonNode root;
            try (InputStream inputStream = metadataResource.getInputStream()) {
                root = objectMapper.readTree(inputStream);
            }
            List<String> featureColumns = new ArrayList<>();
            JsonNode featureNode = root.path("feature_columns");
            if (featureNode.isArray()) {
                for (JsonNode node : featureNode) {
                    featureColumns.add(node.asText());
                }
            }
            if (featureColumns.isEmpty()) {
                featureColumns.addAll(defaultInferenceFeatureColumns());
            }
            Map<String, Object> metrics = root.has("metrics")
                    ? objectMapper.convertValue(root.get("metrics"), new TypeReference<Map<String, Object>>() {
            })
                    : Map.of();
            if (metrics == null) {
                metrics = Map.of();
            }
            Map<String, Double> featureMedians = root.has("feature_medians")
                    ? parseFeatureMedians(root.get("feature_medians"))
                    : Map.of();
            String version = root.path("version").asText(properties.getModelVersionFallback());
            ModelMeta loaded = new ModelMeta(List.copyOf(featureColumns), Map.copyOf(metrics), Map.copyOf(featureMedians), version);
            modelMeta = loaded;
            return loaded;
        } catch (IOException ex) {
            logger.warn("Failed to read or parse model metadata, fallback to defaults. metadataLocation={}", metadataLocation, ex);
            ModelMeta fallback = new ModelMeta(defaultInferenceFeatureColumns(), Map.of(), Map.of(), properties.getModelVersionFallback());
            modelMeta = fallback;
            return fallback;
        } catch (RuntimeException ex) {
            logger.warn("Unexpected error while loading model metadata, fallback to defaults. metadataLocation={}", metadataLocation, ex);
            ModelMeta fallback = new ModelMeta(defaultInferenceFeatureColumns(), Map.of(), Map.of(), properties.getModelVersionFallback());
            modelMeta = fallback;
            return fallback;
        }
    }

    private Map<String, Double> parseFeatureMedians(JsonNode featureMediansNode) {
        if (featureMediansNode == null || !featureMediansNode.isObject()) {
            return Map.of();
        }
        Map<String, Double> featureMedians = new LinkedHashMap<>();
        featureMediansNode.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isNumber()) {
                featureMedians.put(entry.getKey(), value.asDouble());
            }
        });
        return featureMedians;
    }

    private float[] alignFeatures(Map<String, Double> featureRow, ModelMeta meta) {
        List<String> featureColumns = meta.featureColumns();
        float[] aligned = new float[featureColumns.size()];
        for (int i = 0; i < featureColumns.size(); i++) {
            String feature = featureColumns.get(i);
            Double value = featureRow.get(feature);
            if (value == null) {
                value = meta.featureMedians().get(feature);
            }
            if (value == null || value.isNaN() || value.isInfinite()) {
                aligned[i] = 0F;
            } else {
                aligned[i] = value.floatValue();
            }
        }
        return aligned;
    }

    private double predictProbability(float[] alignedFeatures) {
        Predictor loadedPredictor = ensurePredictor();
        float[] prediction = loadedPredictor.predict(FVec.Transformer.fromArray(alignedFeatures, false));
        if (prediction == null || prediction.length == 0) {
            throw new IllegalStateException("Model returned empty prediction");
        }
        double probability = prediction.length == 1 ? prediction[0] : prediction[prediction.length - 1];
        if (probability < 0 || probability > 1) {
            probability = sigmoid(probability);
        }
        return clamp(probability, 0.0, 1.0);
    }

    /**
     * 计算顶部特征贡献度。
     *
     * <p>以训练集中位数为基线，计算特征值相对基线的偏差方向，
     * 给出正向/负向贡献，便于医生理解模型判断依据。</p>
     */
    private List<ImldInferenceApiDtos.Response.FeatureContribution> topFeatureContributions(
            float[] alignedFeatures, List<String> featureColumns,
            Map<String, Double> featureMedians, int topK) {
        List<ImldInferenceApiDtos.Response.FeatureContribution> contributions = new ArrayList<>();
        for (int i = 0; i < Math.min(alignedFeatures.length, featureColumns.size()); i++) {
            String feature = featureColumns.get(i);
            double featureValue = alignedFeatures[i];
            double baseline = featureMedians.getOrDefault(feature, 0.0);
            double delta = featureValue - baseline;
            if (Math.abs(delta) < 1e-9) {
                continue;
            }
            double contribution = Math.signum(delta) * Math.log1p(Math.abs(delta));
            String direction = delta > 0 ? "POSITIVE" : "NEGATIVE";
            contributions.add(new ImldInferenceApiDtos.Response.FeatureContribution(
                    feature,
                    round6(featureValue),
                    round6(baseline),
                    round6(contribution),
                    direction,
                    "MEDIAN_DELTA_APPROXIMATION"
            ));
        }
        return contributions.stream()
                .sorted(Comparator.comparingDouble(value -> -Math.abs(value.contribution())))
                .limit(topK)
                .toList();
    }

    private String riskLevelFromProbability(double probability) {
        if (probability < properties.getLowRiskThreshold()) {
            return DEFAULT_RISK_LEVEL_LOW;
        }
        if (probability < properties.getHighRiskThreshold()) {
            return DEFAULT_RISK_LEVEL_MEDIUM;
        }
        return DEFAULT_RISK_LEVEL_HIGH;
    }

    private double numericalFuzzification(double value, double normalMin, double normalMax, SplittableRandom random) {
        double epsilon = (value >= normalMin && value < normalMax)
                ? random.nextDouble(-0.05, 0.05)
                : random.nextDouble(-0.03, 0.03);
        double fuzzed = value * (1 + epsilon);
        if ((value >= normalMin && value < normalMax) && !(fuzzed >= normalMin && fuzzed < normalMax)) {
            fuzzed = clamp(fuzzed, normalMin, normalMax - 1e-6);
        }
        return round4(fuzzed);
    }

    private List<NormalizedVariant> normalizeVariants(List<ImldInferenceApiDtos.Request.GeneVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }
        return variants.stream()
                .filter(Objects::nonNull)
                .map(item -> new NormalizedVariant(
                        normalizeUpper(item.gene(), "UNKNOWN"),
                        nullIfBlank(item.cChange()),
                        nullIfBlank(item.pChange()),
                        normalizeLower(item.variantType(), "unknown"),
                        normalizeLower(item.zygosity(), "unknown"),
                        normalizeLower(item.pathogenicity(), "unknown"),
                        item.alleleFrequency()
                ))
                .toList();
    }

    private double singleVariantRiskScore(NormalizedVariant variant) {
        double geneWeight = GENE_PANEL_WEIGHTS.getOrDefault(variant.gene(), 0.35);
        double typeWeight = VARIANT_TYPE_WEIGHTS.getOrDefault(variant.variantType(), 0.4);
        double zygosityWeight = ZYGOSITY_WEIGHTS.getOrDefault(variant.zygosity(), 0.5);
        double pathScore = pathogenicityScore(variant.pathogenicity());
        double afScore = rarityScore(variant.alleleFrequency());
        return round6(geneWeight * (
                0.40 * pathScore
                        + 0.20 * typeWeight
                        + 0.20 * zygosityWeight
                        + 0.20 * afScore
        ));
    }

    private double pathogenicityScore(String label) {
        return switch (label) {
            case "benign" -> 0.0;
            case "likely_benign" -> 0.1;
            case "vus" -> 0.4;
            case "likely_pathogenic" -> 0.8;
            case "pathogenic" -> 1.0;
            default -> 0.3;
        };
    }

    private double rarityScore(Double alleleFrequency) {
        if (alleleFrequency == null) {
            return 0.5;
        }
        if (alleleFrequency < 0.001) {
            return 1.0;
        }
        if (alleleFrequency < 0.01) {
            return 0.8;
        }
        if (alleleFrequency < 0.05) {
            return 0.4;
        }
        return 0.1;
    }

    private String sha256OfObject(Object obj) {
        JsonMapper canonical = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();

        String text;
        try {
            text = canonical.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize payload for hashing", ex);
        }
        return sha256Hex(text);
    }

    private long stableSeed(ImldInferenceApiDtos.Request.ImldPredictRequest payload,
                            List<NormalizedVariant> variants,
                            boolean includeNasScore) {
        String variantFingerprint = variants.stream()
                .map(v -> String.join("|",
                        v.gene(),
                        nullToEmpty(v.cChange()),
                        nullToEmpty(v.pChange()),
                        v.variantType(),
                        v.zygosity(),
                        v.pathogenicity(),
                        v.alleleFrequency() == null ? "" : String.valueOf(v.alleleFrequency())))
                .collect(Collectors.joining(";"));
        String clinicalFingerprint = payload.clinicalFeatures() == null || payload.clinicalFeatures().isEmpty()
                ? ""
                : payload.clinicalFeatures().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(";"));
        String fingerprint = String.join("#",
                String.valueOf(payload.age()),
                String.valueOf(payload.gender()),
                String.valueOf(payload.ALT()),
                String.valueOf(payload.bilirubin()),
                String.valueOf(payload.ceruloplasmin()),
                String.valueOf(payload.jaundice()),
                includeNasScore ? String.valueOf(payload.nasScore()) : "",
                nullToEmpty(payload.patientId()),
                clinicalFingerprint,
                variantFingerprint
        );
        byte[] digest = sha256Bytes(fingerprint);
        return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    }

    private String sha256Hex(String text) {
        byte[] digest = sha256Bytes(text);
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] sha256Bytes(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not supported", ex);
        }
    }

    private List<String> defaultInferenceFeatureColumns() {
        List<String> columns = new ArrayList<>(DEFAULT_FEATURE_COLUMNS);
        for (String gene : KEY_GENE_FEATURES) {
            columns.add(gene + "_variant_count");
            columns.add(gene + "_pathogenic_score_sum");
        }
        return columns;
    }

    private String resolveResourceLocation(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalArgumentException("configured resource path must not be blank");
        }
        String value = configuredPath.trim();
        if (value.startsWith("classpath:")
                || value.startsWith("file:")
                || value.startsWith("http:")
                || value.startsWith("https:")) {
            return value;
        }
        Path path = Path.of(value);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize().toUri().toString();
    }

    private static String nullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeUpper(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLower(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFeatureName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("μ", "u")
                .replace("µ", "u")
                .replace("（", "(")
                .replace("）", ")")
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("/", "")
                .replace("^", "")
                .replace("(", "")
                .replace(")", "");
    }

    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round4(double value) {
        return round(value, 4);
    }

    private static double round6(double value) {
        return round(value, 6);
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private record NormalizedVariant(
            String gene,
            String cChange,
            String pChange,
            String variantType,
            String zygosity,
            String pathogenicity,
            Double alleleFrequency
    ) {
    }

    private record EncodedGeneFeatures(
            Map<String, Double> features,
            List<ImldInferenceApiDtos.Response.GeneAbnormality> abnormalVariants
    ) {
    }

    private record FeaturePreparation(
            Map<String, Double> featureRow,
            String dataHash,
            List<ImldInferenceApiDtos.Response.ClinicalAbnormality> clinicalAbnormalities,
            List<ImldInferenceApiDtos.Response.GeneAbnormality> geneAbnormalities
    ) {
    }

    private record ModelMeta(
            List<String> featureColumns,
            Map<String, Object> metrics,
            Map<String, Double> featureMedians,
            String version
    ) {
    }
}
