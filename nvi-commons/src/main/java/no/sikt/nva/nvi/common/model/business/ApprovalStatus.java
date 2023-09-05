package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             Status status,
                             Username finalizedBy,
                             Instant finalizedDate) implements DynamoDbModel<ApprovalStatus> {

    public static Builder builder() {
        return new Builder();
    }

    public Builder but() {
        return builder()
                   .withInstitutionId(institutionId)
                   .withStatus(status)
                   .withFinalizedBy(finalizedBy)
                   .withFinalizedDate(finalizedDate);
    }

    public static final class Builder {

        private URI institutionId;
        private Status status;
        private Username finalizedBy;
        private Instant finalizedDate;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withStatus(Status status) {
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
            return new ApprovalStatus(institutionId, status, finalizedBy, finalizedDate);
        }
    }
}
