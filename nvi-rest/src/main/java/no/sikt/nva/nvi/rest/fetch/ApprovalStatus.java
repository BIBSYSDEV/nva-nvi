package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.model.DbStatus;
import no.sikt.nva.nvi.common.db.model.DbUsername;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             DbStatus status,
                             DbUsername assignee,
                             DbUsername finalizedBy,
                             Instant finalizedDate,
                             String reason) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI institutionId;
        private DbStatus status;
        private DbUsername assignee;
        private DbUsername finalizedBy;
        private Instant finalizedDate;
        private String reason;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withStatus(DbStatus status) {
            this.status = status;
            return this;
        }

        public Builder withAssignee(DbUsername assignee) {
            this.assignee = assignee;
            return this;
        }

        public Builder withFinalizedBy(DbUsername finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }

        public Builder withFinalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public Builder withReason(String reason) {
            this.reason = reason;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(institutionId, status, assignee, finalizedBy, finalizedDate, reason);
        }
    }
}
