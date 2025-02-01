package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.service.model.ApprovalStatus;

public enum CandidateOperation {
  APPROVAL_REJECT("approval/reject-candidate"),
  APPROVAL_APPROVE("approval/approve-candidate"),
  APPROVAL_PENDING("approval/reset-approval");

  private final String value;

  CandidateOperation(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonIgnore
  public static CandidateOperation fromApprovalStatus(ApprovalStatus newStatus) {
    return switch (newStatus) {
      case APPROVED -> APPROVAL_APPROVE;
      case PENDING -> APPROVAL_PENDING;
      case REJECTED -> APPROVAL_REJECT;
    };
  }
}
