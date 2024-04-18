package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type")
@JsonTypeName("Approval")
public record Approval(String institutionId,
                       Map<String, String> labels,
                       ApprovalStatus approvalStatus,
                       InstitutionPoints points,
                       String assignee) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String institutionId;
        private Map<String, String> labels;
        private ApprovalStatus approvalStatus;
        private InstitutionPoints points;
        private String assignee;

        private Builder() {
        }

        public Builder withInstitutionId(String institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public Builder withLabels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder withApprovalStatus(ApprovalStatus approvalStatus) {
            this.approvalStatus = approvalStatus;
            return this;
        }

        public Builder withPoints(InstitutionPoints points) {
            this.points = points;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public Approval build() {
            return new Approval(institutionId, labels, approvalStatus, points, assignee);
        }
    }
}
