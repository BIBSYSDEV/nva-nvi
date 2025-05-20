package no.sikt.nva.nvi.common.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.model.ParsableEnum;

public enum DataEntryType implements ParsableEnum {
  CANDIDATE_DAO("CandidateDao"),
  APPROVAL_STATUS_DAO("ApprovalStatusDao");

  private final String value;

  DataEntryType(String value) {
    this.value = value;
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static DataEntryType parse(String stringValue) {
    return ParsableEnum.parse(DataEntryType.class, stringValue);
  }
}
