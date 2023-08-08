package no.sikt.nva.nvi.common.model;

import java.net.URI;
import java.time.Instant;

public record InstitutionReportingStatus(URI institutionId,
                                         NviPeriod period,
                                         CompletionStatus status,
                                         Instant updatedDate) {

    public static final class Builder {

        private URI institutionId;
        private NviPeriod period;
        private CompletionStatus status;
        private Instant updatedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withPeriod(NviPeriod period) {
            this.period = period;
            return this;
        }

        public Builder withStatus(CompletionStatus status) {
            this.status = status;
            return this;
        }

        public Builder withUpdatedDate(Instant updatedDate) {
            this.updatedDate = updatedDate;
            return this;
        }

        public InstitutionReportingStatus build() {
            return new InstitutionReportingStatus(institutionId, period, status, updatedDate);
        }
    }
}
