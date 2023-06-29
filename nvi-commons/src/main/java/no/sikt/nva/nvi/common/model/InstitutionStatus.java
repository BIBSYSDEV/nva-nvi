package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record InstitutionStatus(URI institutionId,
                                ApprovalStatus approvalStatus) {

    public static class Builder {

        private URI institutionId;
        private ApprovalStatus approvalStatus;

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public InstitutionStatus build() {
            return new InstitutionStatus(institutionId, approvalStatus);
        }
    }
}
