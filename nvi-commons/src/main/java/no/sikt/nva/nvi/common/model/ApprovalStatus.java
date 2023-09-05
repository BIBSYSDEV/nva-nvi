package no.sikt.nva.nvi.common.model;

import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.DbApprovalStatus;
import nva.commons.core.JacocoGenerated;

// Unused getter of identifier
@JacocoGenerated
public record ApprovalStatus(
    UUID identifier,
    DbApprovalStatus approvalStatus
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID identifier;
        private DbApprovalStatus approvalStatus;

        private Builder() {
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withApprovalStatus(DbApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(identifier, approvalStatus);
        }
    }
}
