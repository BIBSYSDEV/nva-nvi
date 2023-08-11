package no.sikt.nva.nvi.common.model;

import java.time.Instant;

public record ReportingStatus(Institution institution,
                              Period period,
                              CompletionStatus status,
                              Instant updatedDate) {

    public static final class Builder {

        private Institution institution;
        private Period period;
        private CompletionStatus status;
        private Instant updatedDate;

        public Builder() {
        }

        public Builder withInstitution(Institution institution) {
            this.institution = institution;
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
            return new ReportingStatus(institution, period, status, updatedDate);
        }
    }
}
