package xenosoft.imldintelligence.module.diagnoses.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "imld.inference.imld")
public class ImldInferenceProperties {
    private boolean enabled = true;
    private boolean desensitizedClinicalEnabled = true;
    private String modelFilePath = "classpath:models/imld/imld_xgboost_model.bin";
    private String metadataFilePath = "classpath:models/imld/imld_model_meta.json";
    private String modelVersionFallback = "v2_gene_clinical_fusion";
    private String engine = "xgboost-java";
    private int maxBatchSize = 100;
    private double lowRiskThreshold = 0.2;
    private double highRiskThreshold = 0.7;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDesensitizedClinicalEnabled() {
        return desensitizedClinicalEnabled;
    }

    public void setDesensitizedClinicalEnabled(boolean desensitizedClinicalEnabled) {
        this.desensitizedClinicalEnabled = desensitizedClinicalEnabled;
    }

    public String getModelFilePath() {
        return modelFilePath;
    }

    public void setModelFilePath(String modelFilePath) {
        this.modelFilePath = modelFilePath;
    }

    public String getMetadataFilePath() {
        return metadataFilePath;
    }

    public void setMetadataFilePath(String metadataFilePath) {
        this.metadataFilePath = metadataFilePath;
    }

    public String getModelVersionFallback() {
        return modelVersionFallback;
    }

    public void setModelVersionFallback(String modelVersionFallback) {
        this.modelVersionFallback = modelVersionFallback;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public double getLowRiskThreshold() {
        return lowRiskThreshold;
    }

    public void setLowRiskThreshold(double lowRiskThreshold) {
        this.lowRiskThreshold = lowRiskThreshold;
    }

    public double getHighRiskThreshold() {
        return highRiskThreshold;
    }

    public void setHighRiskThreshold(double highRiskThreshold) {
        this.highRiskThreshold = highRiskThreshold;
    }
}
