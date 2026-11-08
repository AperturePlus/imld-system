package xenosoft.imldintelligence.module.clinical.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 电子病历详情查询接口的响应 DTO。
 *
 * <p>字段结构与前端 {@code PatientRecordPayload} 对齐，用于把一次就诊的病历数据聚合返回。
 * 历史数据中尚未采集的字段可能返回 {@code null}，新归档数据会持久化完整基础资料。</p>
 */
public final class PatientRecordDetailDtos {

    private PatientRecordDetailDtos() {
    }

    /**
     * 响应结构目录。
     */
    public static final class Response {

        private Response() {
        }

        /**
         * 电子病历详情主体。
         */
        public record PatientRecordDetailResponse(
                String patientNo,
                String name,
                String gender,
                Integer age,
                String visitDate,
                String phone,
                String idCard,
                String occupation,
                String currentAddress,
                String nativePlace,
                String department,
                String encounterType,
                Boolean consanguinity,
                String chiefComplaint,
                String presentIllness,
                JsonNode history,
                JsonNode physicalExam,
                JsonNode laboratoryScreening,
                List<ImagingReportItem> imagingReports,
                PathologyRecord pathology,
                GeneticSequencingRecord geneticSequencing,
                ClinicalDecisionRecord clinicalDecision,
                String visitId
        ) {
        }

        /**
         * 影像报告条目。
         */
        public record ImagingReportItem(
                String modality,
                String reportText,
                String examinedAt,
                String fileId,
                String sourceType
        ) {
        }

        /**
         * 病理报告。
         */
        public record PathologyRecord(
                Boolean performed,
                String reportText,
                Integer nasScore,
                String reportedAt,
                String fileId,
                String sourceType
        ) {
        }

        /**
         * 基因变异位点。
         */
        public record GeneticVariantItem(
                String gene,
                String hgvsC,
                String hgvsP,
                String variantType,
                String zygosity,
                String classification,
                String evidence
        ) {
        }

        /**
         * 基因测序结果。
         */
        public record GeneticSequencingRecord(
                Boolean tested,
                String method,
                String reportSource,
                String reportDate,
                String summary,
                String conclusion,
                String fileId,
                String sourceType,
                List<GeneticVariantItem> variants
        ) {
        }

        /**
         * 临床决策。
         */
        public record ClinicalDecisionRecord(
                String diagnosis,
                String treatmentPlan
        ) {
        }
    }
}
