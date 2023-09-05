package no.sikt.nva.nvi.common.model;

import java.util.UUID;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import nva.commons.core.JacocoGenerated;

// Unused getter of identifier
@JacocoGenerated
public record ApprovalStatusWithIdentifier(
    UUID identifier,
    ApprovalStatus approvalStatus
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private UUID identifier;
        private ApprovalStatus approvalStatus;

        private Builder() {
        }

        public Builder withIdentifier(UUID identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public ApprovalStatusWithIdentifier build() {
            return new ApprovalStatusWithIdentifier(identifier, approvalStatus);
        }
    }
}
