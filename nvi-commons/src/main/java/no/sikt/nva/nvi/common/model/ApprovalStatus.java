package no.sikt.nva.nvi.common.model;

import java.net.URI;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

public class ApprovalStatus {

    private final Status status;
    private final URI approvedBy;
    private final URI approvalDate;

    public ApprovalStatus(Builder builder) {
        this.status = builder.status;
        this.approvedBy = builder.approvedBy;
        this.approvalDate = builder.approvalDate;
    }

    public Status getStatus() {
        return status;
    }

    public URI getApprovedBy() {
        return approvedBy;
    }

    public URI getApprovalDate() {
        return approvalDate;
    }

    public static class Builder {

        private Status status;
        private URI approvedBy;
        private URI approvalDate;

        public Builder withStatus(Status status) {
            this.status = status;
            return this;
        }

        public Builder withApprovedBy(URI approvedBy) {
            this.approvedBy = approvedBy;
            return this;
        }

        public Builder withApprovalDate(URI approvalDate) {
            this.approvalDate = approvalDate;
            return this;
        }

        public ApprovalStatus build() {
            return new ApprovalStatus(this);
        }

        @JacocoGenerated
        @Override
        public int hashCode() {
            return Objects.hash(status, approvedBy, approvalDate);
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
            Builder builder = (Builder) o;
            return status == builder.status
                   && Objects.equals(approvedBy, builder.approvedBy)
                   && Objects.equals(approvalDate, builder.approvalDate);
        }
    }
}
