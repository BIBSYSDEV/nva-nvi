package no.sikt.nva.nvi.common.model;

import no.sikt.nva.nvi.common.db.model.DbStatus;

public record UpdateStatusRequest(DbStatus approvalStatus,
                                  String username,
                                  String reason) implements UpdateApprovalRequest {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private DbStatus approvalStatus;
        private String username;
        private String reason;

        private Builder() {
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
            return new UpdateStatusRequest(approvalStatus, username, reason);
        }
    }
}
