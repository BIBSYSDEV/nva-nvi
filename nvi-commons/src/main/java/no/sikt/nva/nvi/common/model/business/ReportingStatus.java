package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.time.Instant;

public record ReportingStatus(URI institutionId,
                              Period period,
                              CompletionStatus status,
                              Instant updatedDate) {

    public static final class Builder {

        private URI institutionId;
        private Period period;
        private CompletionStatus status;
        private Instant updatedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withPeriod(Period period) {
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

        public ReportingStatus build() {
            return new ReportingStatus(institutionId, period, status, updatedDate);
        }
    }
}
