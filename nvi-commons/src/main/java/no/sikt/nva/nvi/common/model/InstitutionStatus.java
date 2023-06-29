package no.sikt.nva.nvi.common.model;

import java.net.URI;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class InstitutionStatus {

    private final URI institutionId;
    private final ApprovalStatus approvalStatus;

    public InstitutionStatus(Builder builder) {
        this.institutionId = builder.institutionId;
        this.approvalStatus = builder.approvalStatus;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getInstitutionId(), getApprovalStatus());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstitutionStatus that = (InstitutionStatus) o;
        return Objects.equals(getInstitutionId(), that.getInstitutionId())
               && getApprovalStatus() == that.getApprovalStatus();
    }

    public URI getInstitutionId() {
        return institutionId;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public static class Builder {

        private URI institutionId;
        private ApprovalStatus approvalStatus;

        public Builder withInstitutionId(URI institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public InstitutionStatus build() {
            return new InstitutionStatus(this);
        }
    }
}
