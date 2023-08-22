package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.time.Instant;

public record ReportingStatus(URI institutionId,
                              NviPeriod nviPeriod,
                              CompletionStatus status,
                              Instant updatedDate) {

    public static final class Builder {

        private URI institutionId;
        private NviPeriod nviPeriod;
        private CompletionStatus status;
        private Instant updatedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withPeriod(NviPeriod nviPeriod) {
            this.nviPeriod = nviPeriod;
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
            return new ReportingStatus(institutionId, nviPeriod, status, updatedDate);
        }
    }
}
