package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.Candidate;

public record ReportStatusDto(URI publicationId, StatusDto status, String year) {

    public static ReportStatusDto fromCandidate(Candidate candidate) {
        if (candidate.isReported()) {
            return new ReportStatusDto(candidate.getPublicationId(), StatusDto.REPORTED, candidate.getPeriod().year());
        } else if (candidate.isPendingReview()) {
            return new ReportStatusDto(candidate.getPublicationId(), StatusDto.PENDING_REVIEW, candidate.getPeriod().year());
        }
        return null;
    }

    public enum StatusDto {

        PENDING_REVIEW("Pending review"),
        UNDER_REVIEW("Under review"),
        REPORTED("Reported"),
        NOT_REPORTED("Not reported in closed period"),
        NOT_CANDIDATE("Not candidate");
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
