package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonValue;

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
}
