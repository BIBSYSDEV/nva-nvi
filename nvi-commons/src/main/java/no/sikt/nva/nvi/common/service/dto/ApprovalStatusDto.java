package no.sikt.nva.nvi.common.service.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.service.model.Approval;

public enum ApprovalStatusDto {
  NEW("New"),
  PENDING("Pending"),
  APPROVED("Approved"),
  REJECTED("Rejected"),
  NONE("None");

  @JsonValue private final String value;

  ApprovalStatusDto(String value) {
    this.value = value;
  }

  public static ApprovalStatusDto from(Approval approval) {
    return switch (approval.getStatus()) {
      case PENDING -> approval.isAssigned() ? PENDING : NEW;
      case APPROVED -> APPROVED;
      case REJECTED -> REJECTED;
      case NONE -> NONE;
    };
  }
}
