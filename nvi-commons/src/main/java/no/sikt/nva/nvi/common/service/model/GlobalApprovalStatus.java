package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.model.ParsableEnum;

public enum GlobalApprovalStatus implements ParsableEnum {
  APPROVED("Approved"),
  PENDING("Pending"),
  REJECTED("Rejected"),
  DISPUTE("Dispute");

  private final String value;

  GlobalApprovalStatus(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static GlobalApprovalStatus parse(String stringValue) {
    return ParsableEnum.parse(GlobalApprovalStatus.class, stringValue);
  }
}
