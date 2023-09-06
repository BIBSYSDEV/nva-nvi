package no.sikt.nva.nvi.fetch;

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
                             DbUsername finalizedBy,
                             Instant finalizedDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI institutionId;
        private DbStatus status;
        private DbUsername finalizedBy;
        private Instant finalizedDate;

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

        public Builder withFinalizedBy(DbUsername finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }

        public Builder withFinalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(institutionId, status, finalizedBy, finalizedDate);
        }
    }
}
