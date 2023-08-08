package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             Approval approval,
                             Username finalizedBy,
                             Instant finalizedDate) {

    public static final class Builder {

        private URI institutionId;
        private Approval approval;
        private Username finalizedBy;
        private Instant finalizedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withApproval(Approval approval) {
            this.approval = approval;
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
            return new ApprovalStatus(institutionId, approval, finalizedBy, finalizedDate);
        }
    }
}
