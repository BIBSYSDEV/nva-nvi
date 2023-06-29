package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(Status status,
                             URI approvedBy,
                             Instant approvalDate) {

    public static class Builder {

        private Status status;
        private URI approvedBy;
        private Instant approvalDate;

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withApprovedBy(URI approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder withApprovalDate(Instant approvalDate) {
            this.approvalDate = approvalDate;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(status, approvedBy, approvalDate);
        }
    }
}
