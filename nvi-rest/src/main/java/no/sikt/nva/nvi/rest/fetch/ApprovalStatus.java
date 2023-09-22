package no.sikt.nva.nvi.rest.fetch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.model.DbUsername;
import no.sikt.nva.nvi.rest.upsert.NviApprovalStatus;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalStatus(URI institutionId,
                             NviApprovalStatus status,
                             BigDecimal points,
                             DbUsername assignee,
                             DbUsername finalizedBy,
                             Instant finalizedDate) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI institutionId;
        private NviApprovalStatus status;
        private BigDecimal points;
        private DbUsername assignee;
        private DbUsername finalizedBy;
        private Instant finalizedDate;

        private Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withStatus(NviApprovalStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPoints(BigDecimal points) {
            this.points = points;
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

        public ApprovalStatus build() {
            return new ApprovalStatus(institutionId, status, points, assignee, finalizedBy, finalizedDate);
        }
    }
}
