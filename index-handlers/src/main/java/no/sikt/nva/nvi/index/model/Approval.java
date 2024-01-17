package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record Approval(String id,
                       Map<String, String> labels,
                       ApprovalStatus approvalStatus,
                       BigDecimal points,
                       String assignee) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String id;
        private Map<String, String> labels;
        private ApprovalStatus approvalStatus;
        private BigDecimal points;
        private String assignee;

        private Builder() {
        }

        public Builder withId(String id) {
            this.id = id;
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

        public Builder withPoints(BigDecimal points) {
            this.points = points;
            return this;
        }

        public Builder withAssignee(String assignee) {
            this.assignee = assignee;
            return this;
        }

        public Approval build() {
            return new Approval(id, labels, approvalStatus, points, assignee);
        }
    }
}
