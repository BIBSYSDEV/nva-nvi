package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import no.sikt.nva.nvi.common.service.model.Approval;

@JsonTypeName(ApprovalDto.APPROVAL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record ApprovalDto(
    URI institutionId,
    ApprovalStatusDto status,
    BigDecimal points,
    String assignee,
    String finalizedBy,
    Instant finalizedDate,
    String reason) {

  public static final String APPROVAL = "Approval";

  public static Builder builder() {
    return new Builder();
  }

  public static ApprovalDto fromApprovalAndInstitutionPoints(Approval approval, BigDecimal points) {
    return builder()
        .withInstitutionId(approval.institutionId())
        .withStatus(ApprovalStatusDto.from(approval))
        .withPoints(points)
        .withAssignee(approval.getAssigneeUsername())
        .withFinalizedBy(approval.getFinalizedByUserName())
        .withFinalizedDate(approval.finalizedDate())
        .withReason(approval.reason())
        .build();
  }

  public static final class Builder {

    private URI institutionId;
    private ApprovalStatusDto status;
    private BigDecimal points;
    private String assignee;
    private String finalizedBy;
    private Instant finalizedDate;
    private String reason;

    private Builder() {}

    public Builder withInstitutionId(URI institutionId) {
      this.institutionId = institutionId;
      return this;
    }

    public Builder withStatus(ApprovalStatusDto status) {
      this.status = status;
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

    public Builder withFinalizedBy(String finalizedBy) {
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

    public ApprovalDto build() {
      return new ApprovalDto(
          institutionId, status, points, assignee, finalizedBy, finalizedDate, reason);
    }
  }
}
