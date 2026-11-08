package xenosoft.imldintelligence.module.clinical.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

public final class PatientRecordApiDtos {
    private PatientRecordApiDtos() {
    }

    public static final class Request {
        private Request() {
        }

        public record ImagingReportItem(
                @NotBlank(message = "modality must not be blank")
                @Size(max = 32, message = "modality must be at most 32 characters")
                String modality,
                String reportText,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate examinedAt,
                String fileId,
                @Size(max = 32, message = "sourceType must be at most 32 characters")
                String sourceType
        ) {
        }

        public record PathologyRecord(
                @NotNull(message = "performed must not be null")
                Boolean performed,
                String reportText,
                @Min(value = 0, message = "nasScore must be at least 0")
                @Max(value = 8, message = "nasScore must be at most 8")
                Integer nasScore,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate reportedAt,
                String fileId,
                @Size(max = 32, message = "sourceType must be at most 32 characters")
                String sourceType
        ) {
        }

        public record GeneticVariantItem(
                @NotBlank(message = "gene must not be blank")
                @Size(max = 64, message = "gene must be at most 64 characters")
                String gene,
                String hgvsC,
                String hgvsP,
                String variantType,
                String zygosity,
                String classification,
                String evidence
        ) {
        }

        public record GeneticSequencingRecord(
                @NotNull(message = "tested must not be null")
                Boolean tested,
                @Size(max = 32, message = "method must be at most 32 characters")
                String method,
                @Size(max = 128, message = "reportSource must be at most 128 characters")
                String reportSource,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate reportDate,
                String summary,
                String conclusion,
                String fileId,
                @Size(max = 32, message = "sourceType must be at most 32 characters")
                String sourceType,
                List<@Valid GeneticVariantItem> variants
        ) {
        }

        public record ClinicalDecisionRecord(
                @NotBlank(message = "diagnosis must not be blank")
                String diagnosis,
                String treatmentPlan
        ) {
        }

        public record CreatePatientRecordRequest(
                @NotBlank(message = "patientNo must not be blank")
                @Size(max = 64, message = "patientNo must be at most 64 characters")
                String patientNo,
                @NotBlank(message = "name must not be blank")
                @Size(max = 100, message = "name must be at most 100 characters")
                String name,
                @Size(max = 16, message = "gender must be at most 16 characters")
                String gender,
                @Min(value = 0, message = "age must be at least 0")
                @Max(value = 130, message = "age must be at most 130")
                Integer age,
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate visitDate,
                String phone,
                String idCard,
                @NotBlank(message = "occupation must not be blank")
                @Size(max = 100, message = "occupation must be at most 100 characters")
                String occupation,
                @NotBlank(message = "currentAddress must not be blank")
                @Size(max = 500, message = "currentAddress must be at most 500 characters")
                String currentAddress,
                @NotBlank(message = "nativePlace must not be blank")
                @Size(max = 200, message = "nativePlace must be at most 200 characters")
                String nativePlace,
                @NotBlank(message = "department must not be blank")
                String department,
                @NotBlank(message = "encounterType must not be blank")
                String encounterType,
                Boolean consanguinity,
                @NotBlank(message = "chiefComplaint must not be blank")
                String chiefComplaint,
                @NotBlank(message = "presentIllness must not be blank")
                String presentIllness,
                JsonNode history,
                JsonNode physicalExam,
                JsonNode laboratoryScreening,
                List<@Valid ImagingReportItem> imagingReports,
                @Valid PathologyRecord pathology,
                @Valid GeneticSequencingRecord geneticSequencing,
                @Valid ClinicalDecisionRecord clinicalDecision,
                String visitId,
                JsonNode importMeta
        ) {
        }
    }

    public static final class Response {
        private Response() {
        }

        public record PatientRecordCreateResponse(
                String recordId,
                String visitId,
                String message
        ) {
        }
    }
}
