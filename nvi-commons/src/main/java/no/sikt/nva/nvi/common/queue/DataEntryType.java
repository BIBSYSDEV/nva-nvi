package no.sikt.nva.nvi.common.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.model.ParsableEnum;

public enum DataEntryType implements ParsableEnum {
  UniquenessEntry("CandidateUniquenessEntry"),
  CANDIDATE("Candidate"),
  NON_CANDIDATE("NonCandidate"),
  APPROVAL_STATUS("ApprovalStatus"),
  UNKNOWN("UnknownType");

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
    return ParsableEnum.parse(DataEntryType.class, stringValue, UNKNOWN);
  }

  @JsonIgnore
  public boolean shouldBeProcessedForIndexing() {
    return switch (this) {
      case CANDIDATE, NON_CANDIDATE, APPROVAL_STATUS -> true;
      default -> false;
    };
  }
}
