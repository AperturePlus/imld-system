package xenosoft.imldintelligence.module.diagnoses.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class ImldInferenceApiDtos {
    private ImldInferenceApiDtos() {
    }

    public static final class Request {
        private Request() {
        }

        public record GeneVariant(
                @NotNull
                @Size(min = 1, max = 32)
                String gene,
                @JsonProperty("c_change")
                @Size(max = 64)
                String cChange,
                @JsonProperty("p_change")
                @Size(max = 64)
                String pChange,
                @JsonProperty("variant_type")
                @Size(max = 32)
                String variantType,
                @Size(max = 32)
                String zygosity,
                @Size(max = 32)
                String pathogenicity,
                @JsonProperty("allele_frequency")
                @DecimalMin(value = "0.0")
                @DecimalMax(value = "1.0")
                Double alleleFrequency
        ) {
        }

        public record ImldPredictRequest(
                @NotNull
                @Min(1)
                @Max(120)
                Integer age,
                @NotNull
                @Min(0)
                @Max(1)
                Integer gender,
                @NotNull
                @DecimalMin(value = "0.0")
                Double ALT,
                @NotNull
                @DecimalMin(value = "0.0")
                Double bilirubin,
                @NotNull
                @DecimalMin(value = "0.0")
                Double ceruloplasmin,
                @NotNull
                @Min(0)
                @Max(1)
                Integer jaundice,
                @Min(0)
                @Max(8)
                Integer nasScore,
                @JsonProperty("gene_variants")
                List<@Valid GeneVariant> geneVariants,
                @JsonProperty("clinical_features")
                Map<String, Double> clinicalFeatures,
                @JsonProperty("patient_id")
                @Size(max = 128)
                String patientId
        ) {
        }
    }

    public static final class Response {
        private Response() {
        }

        public record HealthResponse(
                String service,
                @JsonProperty("model_loaded")
                boolean modelLoaded,
                @JsonProperty("model_version")
                String modelVersion,
                @JsonProperty("model_metrics")
                Map<String, Object> modelMetrics,
                String engine
        ) {
        }

        public record ClinicalAbnormality(
                String feature,
                double value,
                @JsonProperty("normal_range")
                List<Double> normalRange,
                String direction,
                String severity
        ) {
        }

        public record GeneAbnormality(
                String gene,
                @JsonProperty("c_change")
                String cChange,
                @JsonProperty("p_change")
                String pChange,
                @JsonProperty("variant_type")
                String variantType,
                String zygosity,
                String pathogenicity,
                @JsonProperty("allele_frequency")
                Double alleleFrequency,
                @JsonProperty("variant_risk_score")
                double variantRiskScore,
                String reason
        ) {
        }

        /**
         * 特征贡献度。
         *
         * <p>除原始贡献值外，额外给出特征值、训练集基线和贡献方向，
         * 便于医生理解模型为何判定高风险或低风险。</p>
         */
        public record FeatureContribution(
                String feature,
                double featureValue,
                double baseline,
                double contribution,
                String direction,
                String method
        ) {
        }

        public record PredictData(
                @JsonProperty("patient_id")
                String patientId,
                @JsonProperty("risk_probability")
                double riskProbability,
                @JsonProperty("risk_label")
                int riskLabel,
                @JsonProperty("risk_level")
                String riskLevel,
                @JsonProperty("desensitized_flag")
                boolean desensitizedFlag,
                @JsonProperty("data_hash")
                String dataHash,
                @JsonProperty("clinical_abnormalities")
                List<ClinicalAbnormality> clinicalAbnormalities,
                @JsonProperty("gene_abnormalities")
                List<GeneAbnormality> geneAbnormalities,
                @JsonProperty("top_feature_contributions")
                List<FeatureContribution> topFeatureContributions,
                List<String> suggestions,
                @JsonProperty("model_metrics")
                Map<String, Object> modelMetrics
        ) {
        }

        public record BatchPredictItem(
                @JsonProperty("sample_index")
                int sampleIndex,
                @JsonProperty("patient_id")
                String patientId,
                @JsonProperty("risk_probability")
                double riskProbability,
                @JsonProperty("risk_label")
                int riskLabel,
                @JsonProperty("risk_level")
                String riskLevel,
                @JsonProperty("clinical_abnormality_count")
                int clinicalAbnormalityCount,
                @JsonProperty("gene_abnormality_count")
                int geneAbnormalityCount,
                @JsonProperty("data_hash")
                String dataHash
        ) {
        }

        public record BatchPredictData(
                @JsonProperty("batch_count")
                int batchCount,
                @JsonProperty("batch_results")
                List<BatchPredictItem> batchResults,
                @JsonProperty("desensitized_flag")
                boolean desensitizedFlag,
                @JsonProperty("model_metrics")
                Map<String, Object> modelMetrics
        ) {
        }
    }
}
