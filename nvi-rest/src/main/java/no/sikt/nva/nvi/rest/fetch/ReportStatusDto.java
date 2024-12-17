package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.Candidate;

public record ReportStatusDto(URI publicationId, StatusDto status, String period) {

    public static ReportStatusDto fromCandidate(Candidate candidate) {
        return new ReportStatusDto(candidate.getPublicationId(), getStatus(candidate), candidate.getPeriod().year());
    }

    public static Builder builder() {
        return new Builder();
    }

    private static StatusDto getStatus(Candidate candidate) {
        if (!candidate.isApplicable()) {
            return StatusDto.NOT_CANDIDATE;
        } else if (candidate.isReported()) {
            return StatusDto.REPORTED;
        } else if (candidate.isPendingReview()) {
            return StatusDto.PENDING_REVIEW;
        } else if (candidate.isUnderReview()) {
            return StatusDto.UNDER_REVIEW;
        } else if (candidate.isNotReportedInClosedPeriod()) {
            return StatusDto.NOT_REPORTED;
        } else {
            throw new IllegalStateException("Unable to determine status for candidate");
        }
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

    public static final class Builder {

        private URI publicationId;
        private StatusDto status;

        private String year;

        private Builder() {
        }

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withStatus(StatusDto status) {
            this.status = status;
            return this;
        }

        public Builder withYear(String year) {
            this.year = year;
            return this;
        }

        public ReportStatusDto build() {
            return new ReportStatusDto(publicationId, status, year);
        }
    }
}
