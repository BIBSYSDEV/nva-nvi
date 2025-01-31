package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CandidateOperation {
  APPROVAL_REJECT("reject-candidate"),
  APPROVAL_APPROVE("approve-candidate"),
  APPROVAL_PENDING("reset-approval");

  private final String value;

  CandidateOperation(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
