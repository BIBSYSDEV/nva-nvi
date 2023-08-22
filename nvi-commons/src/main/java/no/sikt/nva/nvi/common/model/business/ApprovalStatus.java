package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.dto.ApprovalStatusDb;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             Status status,
                             Username finalizedBy,
                             Instant finalizedDate) {

    public ApprovalStatusDb toDb() {
        return new ApprovalStatusDb.Builder()
                   .withInstitutionId(institutionId)
                   .withStatus(status.toString())
                   .withFinalizedBy(finalizedBy == null ? null : finalizedBy.toDb())
                   .withFinalizedDate(finalizedDate)
                   .build();
    }

    public static ApprovalStatus fromDb(ApprovalStatusDb db) {
        return new ApprovalStatus.Builder()
                   .withInstitutionId(db.getInstitutionId())
                   .withStatus(Status.valueOf(db.getStatus()))
                   .withFinalizedBy(db.getFinalizedBy() == null ? null : Username.fromDb(db.getFinalizedBy()))
                   .withFinalizedDate(db.getFinalizedDate())
                   .build();
    }

    public static final class Builder {

        private URI institutionId;
        private Status status;
        private Username finalizedBy;
        private Instant finalizedDate;

        public Builder() {
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
