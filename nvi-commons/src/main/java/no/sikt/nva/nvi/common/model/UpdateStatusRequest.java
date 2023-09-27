package no.sikt.nva.nvi.common.model;

import java.net.URI;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao.DbStatus;

public record UpdateStatusRequest(java.net.URI institutionId, DbStatus approvalStatus,
                                  String username,
                                  String reason) implements UpdateApprovalRequest {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI institutionId;
        private DbStatus approvalStatus;
        private String username;
        private String reason;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withApprovalStatus(DbStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public Builder withUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder withReason(String reason) {
            this.reason = reason;
            return this;
        }

        public UpdateStatusRequest build() {
            return new UpdateStatusRequest(institutionId, approvalStatus, username, reason);
        }
    }
}
