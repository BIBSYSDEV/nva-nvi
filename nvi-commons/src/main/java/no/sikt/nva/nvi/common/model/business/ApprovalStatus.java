package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(Institution institution,
                             Status status,
                             Username finalizedBy,
                             Instant finalizedDate) {

    public static final class Builder {

        private Institution institution;
        private Status status;
        private Username finalizedBy;
        private Instant finalizedDate;

        public Builder() {
        }

        public Builder withInstitution(Institution institution) {
            this.institution = institution;
            return this;
        }

        public Builder withApproval(Status status) {
            this.status = status;
            return this;
        }

        public Builder withFinalizedBy(Username finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }

        public Builder withFinalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(institution, status, finalizedBy, finalizedDate);
        }
    }
}
