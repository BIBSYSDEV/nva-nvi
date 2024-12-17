package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.Candidate;

public record ReportStatusDto(URI publicationId, StatusDto status, String year) {

    public static ReportStatusDto fromCandidate(Candidate candidate) {
        return new ReportStatusDto(candidate.getPublicationId(), getStatus(candidate), candidate.getPeriod().year());
    }

    private static StatusDto getStatus(Candidate candidate) {
        if (candidate.isReported()) {
            return StatusDto.REPORTED;
        } else if (candidate.isPendingReview()) {
            return StatusDto.PENDING_REVIEW;
        } else if (candidate.isUnderReview()) {
            return StatusDto.UNDER_REVIEW;
        }
        return null;
    }

    public enum StatusDto {

        PENDING_REVIEW("Pending review. Awaiting approval from all institutions"),
        UNDER_REVIEW("Under review. At least one institution has approved/rejected"),
        REPORTED("Reported"),
        NOT_REPORTED("Not reported in closed period"),
        NOT_CANDIDATE("Not a candidate");
        private final String value;

        StatusDto(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
