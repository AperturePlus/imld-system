package xenosoft.imldintelligence.module.diagnoses.internal.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xenosoft.imldintelligence.module.diagnoses.api.dto.ImldInferenceApiDtos;
import xenosoft.imldintelligence.module.diagnoses.internal.config.ImldInferenceProperties;
import xenosoft.imldintelligence.module.diagnoses.internal.service.ImldInferenceService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@ConditionalOnProperty(prefix = "imld.inference.imld", name = "engine", havingValue = "mock")
public class MockImldInferenceService implements ImldInferenceService {
    private static final String LOW_RISK_LEVEL = "低风险";
    private static final String MEDIUM_RISK_LEVEL = "中风险";
    private static final String HIGH_RISK_LEVEL = "高风险";

    private static final double ALT_UPPER = 40.0;
    private static final double BILIRUBIN_UPPER = 17.1;
    private static final double CERULOPLASMIN_LOWER = 200.0;

    private final ImldInferenceProperties properties;
    private final ObjectMapper canonicalObjectMapper;

    public MockImldInferenceService(ImldInferenceProperties properties) {
        this.properties = properties;
        this.canonicalObjectMapper = JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    @Override
    public ImldInferenceApiDtos.Response.HealthResponse health() {
        return new ImldInferenceApiDtos.Response.HealthResponse(
                "running",
                true,
                "mock-v1",
                Map.of("mode", "deterministic", "engine", "mock"),
                properties.getEngine()
        );
    }

    @Override
    public ImldInferenceApiDtos.Response.PredictData predict(ImldInferenceApiDtos.Request.ImldPredictRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        String dataHash = sha256Hex(request);
        double riskProbability = buildRiskProbability(request, dataHash);
        int riskLabel = riskProbability >= 0.5 ? 1 : 0;
        String riskLevel = riskLevelFromProbability(riskProbability);

        List<ImldInferenceApiDtos.Response.ClinicalAbnormality> clinicalAbnormalities = buildClinicalAbnormalities(request);
        List<ImldInferenceApiDtos.Response.GeneAbnormality> geneAbnormalities = buildGeneAbnormalities(request.geneVariants());
        List<ImldInferenceApiDtos.Response.FeatureContribution> contributions = buildFeatureContributions(request);
        List<String> suggestions = buildSuggestions(riskLevel, geneAbnormalities, clinicalAbnormalities);

        return new ImldInferenceApiDtos.Response.PredictData(
                request.patientId(),
                round(riskProbability, 4),
                riskLabel,
                riskLevel,
                properties.isDesensitizedClinicalEnabled(),
                dataHash,
                clinicalAbnormalities,
                geneAbnormalities,
                contributions,
                suggestions,
                Map.of("mock", true, "version", "v1")
        );
    }

    @Override
    public ImldInferenceApiDtos.Response.BatchPredictData batchPredict(List<ImldInferenceApiDtos.Request.ImldPredictRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("batch payload must not be empty");
        }
        if (requests.size() > properties.getMaxBatchSize()) {
            throw new IllegalArgumentException("batch payload exceeds max size: " + properties.getMaxBatchSize());
        }

        List<ImldInferenceApiDtos.Response.BatchPredictItem> items = new ArrayList<>(requests.size());
        int index = 1;
        for (ImldInferenceApiDtos.Request.ImldPredictRequest request : requests) {
            ImldInferenceApiDtos.Response.PredictData prediction = predict(request);
            items.add(new ImldInferenceApiDtos.Response.BatchPredictItem(
                    index++,
                    prediction.patientId(),
                    prediction.riskProbability(),
                    prediction.riskLabel(),
                    prediction.riskLevel(),
                    prediction.clinicalAbnormalities().size(),
                    prediction.geneAbnormalities().size(),
                    prediction.dataHash()
            ));
        }
        return new ImldInferenceApiDtos.Response.BatchPredictData(
                requests.size(),
                items,
                properties.isDesensitizedClinicalEnabled(),
                Map.of("mock", true, "version", "v1")
        );
    }

    private double buildRiskProbability(ImldInferenceApiDtos.Request.ImldPredictRequest request, String dataHash) {
        int geneCount = request.geneVariants() == null ? 0 : request.geneVariants().size();
        long pathogenicCount = request.geneVariants() == null ? 0 : request.geneVariants().stream()
                .filter(Objects::nonNull)
                .map(ImldInferenceApiDtos.Request.GeneVariant::pathogenicity)
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> "pathogenic".equals(value) || "likely_pathogenic".equals(value))
                .count();

        double ageFactor = clamp(request.age() / 120.0, 0.0, 1.0);
        double altFactor = clamp(request.ALT() / (ALT_UPPER * 3), 0.0, 1.0);
        double bilirubinFactor = clamp(request.bilirubin() / (BILIRUBIN_UPPER * 3), 0.0, 1.0);
        double ceruloplasminRisk = clamp((CERULOPLASMIN_LOWER - request.ceruloplasmin()) / CERULOPLASMIN_LOWER, 0.0, 1.0);
        double jaundiceFactor = request.jaundice() == null ? 0.0 : clamp(request.jaundice(), 0, 1);
        double geneFactor = clamp((geneCount * 0.12) + (pathogenicCount * 0.15), 0.0, 1.0);
        double nasFactor = request.nasScore() == null ? 0.0 : clamp(request.nasScore() / 8.0, 0.0, 1.0);
        double deterministicNoise = deterministicNoise(dataHash);

        double score = (0.18 * ageFactor)
                + (0.18 * altFactor)
                + (0.18 * bilirubinFactor)
                + (0.16 * ceruloplasminRisk)
                + (0.1 * jaundiceFactor)
                + (0.15 * geneFactor)
                + (0.05 * nasFactor)
                + (0.05 * deterministicNoise);
        return clamp(score, 0.0, 1.0);
    }

    private List<ImldInferenceApiDtos.Response.ClinicalAbnormality> buildClinicalAbnormalities(
            ImldInferenceApiDtos.Request.ImldPredictRequest request) {
        List<ImldInferenceApiDtos.Response.ClinicalAbnormality> abnormalities = new ArrayList<>();
        if (request.ALT() > ALT_UPPER) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "ALT",
                    round(request.ALT(), 4),
                    List.of(0.0, ALT_UPPER),
                    "high",
                    request.ALT() >= ALT_UPPER * 2 ? "高" : "中"
            ));
        }
        if (request.bilirubin() > BILIRUBIN_UPPER) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "bilirubin",
                    round(request.bilirubin(), 4),
                    List.of(0.0, BILIRUBIN_UPPER),
                    "high",
                    request.bilirubin() >= BILIRUBIN_UPPER * 2 ? "高" : "中"
            ));
        }
        if (request.ceruloplasmin() < CERULOPLASMIN_LOWER) {
            abnormalities.add(new ImldInferenceApiDtos.Response.ClinicalAbnormality(
                    "ceruloplasmin",
                    round(request.ceruloplasmin(), 4),
                    List.of(CERULOPLASMIN_LOWER, 600.0),
                    "low",
                    request.ceruloplasmin() <= CERULOPLASMIN_LOWER * 0.5 ? "高" : "中"
            ));
        }
        if (request.jaundice() != null && request.jaundice() == 1) {
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

    private List<ImldInferenceApiDtos.Response.GeneAbnormality> buildGeneAbnormalities(
            List<ImldInferenceApiDtos.Request.GeneVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }
        List<ImldInferenceApiDtos.Response.GeneAbnormality> abnormalities = new ArrayList<>();
        for (ImldInferenceApiDtos.Request.GeneVariant variant : variants) {
            if (variant == null) {
                continue;
            }
            String pathogenicity = variant.pathogenicity() == null
                    ? "unknown"
                    : variant.pathogenicity().toLowerCase(Locale.ROOT);
            if (!"pathogenic".equals(pathogenicity) && !"likely_pathogenic".equals(pathogenicity)) {
                continue;
            }
            double variantRiskScore = "pathogenic".equals(pathogenicity) ? 0.9 : 0.75;
            abnormalities.add(new ImldInferenceApiDtos.Response.GeneAbnormality(
                    variant.gene(),
                    variant.cChange(),
                    variant.pChange(),
                    variant.variantType(),
                    variant.zygosity(),
                    pathogenicity,
                    variant.alleleFrequency(),
                    variantRiskScore,
                    "mock inference detected potential high-risk variant"
            ));
        }
        return abnormalities.stream()
                .sorted(Comparator.comparingDouble(ImldInferenceApiDtos.Response.GeneAbnormality::variantRiskScore).reversed())
                .toList();
    }

    private List<ImldInferenceApiDtos.Response.FeatureContribution> buildFeatureContributions(
            ImldInferenceApiDtos.Request.ImldPredictRequest request) {
        Map<String, Double> contributions = new LinkedHashMap<>();
        contributions.put("ALT", round(Math.log1p(Math.max(request.ALT(), 0.0)), 6));
        contributions.put("bilirubin", round(Math.log1p(Math.max(request.bilirubin(), 0.0)), 6));
        contributions.put("ceruloplasmin", round(-Math.log1p(Math.max(request.ceruloplasmin(), 0.0)), 6));
        contributions.put("jaundice", request.jaundice() != null && request.jaundice() == 1 ? 0.65 : 0.0);
        contributions.put("gene_variant_count", request.geneVariants() == null ? 0.0 : request.geneVariants().size() * 0.15);

        return contributions.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue()) > 1e-9)
                .sorted((left, right) -> Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue())))
                .map(entry -> new ImldInferenceApiDtos.Response.FeatureContribution(
                        entry.getKey(),
                        0.0,
                        0.0,
                        round(entry.getValue(), 6),
                        entry.getValue() > 0 ? "POSITIVE" : "NEGATIVE",
                        "MOCK_APPROXIMATION"))
                .limit(8)
                .toList();
    }

    private List<String> buildSuggestions(
            String riskLevel,
            List<ImldInferenceApiDtos.Response.GeneAbnormality> geneAbnormalities,
            List<ImldInferenceApiDtos.Response.ClinicalAbnormality> clinicalAbnormalities) {
        List<String> suggestions = new ArrayList<>();
        switch (riskLevel) {
            case HIGH_RISK_LEVEL -> {
                suggestions.add("建议尽快前往专科医院完善遗传代谢病评估。");
                suggestions.add("建议结合实验室指标与家族史开展复核。");
            }
            case MEDIUM_RISK_LEVEL -> {
                suggestions.add("建议结合随访复查指标评估风险变化。");
                suggestions.add("必要时转诊临床遗传科进行进一步判断。");
            }
            default -> suggestions.add("当前为低风险评估，仍需结合临床医生判断。");
        }
        if (!geneAbnormalities.isEmpty()) {
            suggestions.add("建议优先复核基因位点：" + geneAbnormalities.get(0).gene());
        }
        if (!clinicalAbnormalities.isEmpty()) {
            suggestions.add("建议关注异常临床指标数量：" + clinicalAbnormalities.size());
        }
        return suggestions.stream().limit(5).toList();
    }

    private String riskLevelFromProbability(double probability) {
        if (probability < properties.getLowRiskThreshold()) {
            return LOW_RISK_LEVEL;
        }
        if (probability < properties.getHighRiskThreshold()) {
            return MEDIUM_RISK_LEVEL;
        }
        return HIGH_RISK_LEVEL;
    }

    private String sha256Hex(Object value) {
        try {
            byte[] payload = canonicalObjectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("failed to compute hash for mock inference", ex);
        }
    }

    private double deterministicNoise(String dataHash) {
        long seed = Long.parseUnsignedLong(dataHash.substring(0, 8), 16);
        return (seed % 1000) / 1000.0;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int clamp(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
