package no.sikt.nva.nvi.common.db.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.db.WithCopy;
import no.sikt.nva.nvi.common.db.dto.ApprovalStatusDb.Builder;
public class ApprovalStatusDb implements WithCopy<Builder> {

    public static final String INSTITUTION_ID_FIELD = "closed";
    public static final String STATUS_FIELD = "status";
    public static final String FINALIZED_BY_FIELD = "finalizedBy";
    public static final String FINALIZED_DATE_FIELD = "finalizedDate";
    @JsonProperty(INSTITUTION_ID_FIELD)
    private URI institutionId;
    @JsonProperty(STATUS_FIELD)
    private String status;
    @JsonProperty(FINALIZED_BY_FIELD)
    private UsernameDb finalizedBy;
    @JsonProperty(FINALIZED_DATE_FIELD)
    private Instant finalizedDate;

    public ApprovalStatusDb(URI institutionId, String status, UsernameDb finalizedBy, Instant finalizedDate) {
        this.institutionId = institutionId;
        this.status = status;
        this.finalizedBy = finalizedBy;
        this.finalizedDate = finalizedDate;
    }

    @Override
    public Builder copy() {
        return new Builder().withInstitutionId(institutionId).withStatus(status).withFinalizedBy(finalizedBy).withFinalizedDate(finalizedDate);
    }

    public URI getInstitutionId() {
        return institutionId;
    }

    public String getStatus() {
        return status;
    }

    public UsernameDb getFinalizedBy() {
        return finalizedBy;
    }

    public Instant getFinalizedDate() {
        return finalizedDate;
    }

    public static final class Builder {

        private URI institutionId;
        private String status;
        private UsernameDb finalizedBy;
        private Instant finalizedDate;

        public Builder() {
        }

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }
        public Builder withFinalizedBy(UsernameDb finalizedBy) {
            this.finalizedBy = finalizedBy;
            return this;
        }
        public Builder withFinalizedDate(Instant finalizedDate) {
            this.finalizedDate = finalizedDate;
            return this;
        }

        public ApprovalStatusDb build() {
            return new ApprovalStatusDb(institutionId, status, finalizedBy, finalizedDate);
        }
    }
}
