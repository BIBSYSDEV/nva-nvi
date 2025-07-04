package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.model.ParsableEnum;

public enum ApprovalStatus implements ParsableEnum {
  NEW("New"),
  PENDING("Pending"),
  APPROVED("Approved"),
  REJECTED("Rejected");

  private final String value;

  ApprovalStatus(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ApprovalStatus parse(String stringValue) {
    return ParsableEnum.parse(ApprovalStatus.class, stringValue);
  }
}
