package no.sikt.nva.nvi.index.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import no.sikt.nva.nvi.common.model.ParsableEnum;

public enum QueryFilterType implements ParsableEnum {
  COLLABORATION("collaboration"),
  OTHERS_APPROVE("approvedByOthers"),
  OTHERS_REJECT("rejectedByOthers"),
  NEW_AGG("pending"),
  NEW_COLLABORATION_AGG("pendingCollaboration"),
  PENDING_AGG("assigned"),
  PENDING_COLLABORATION_AGG("assignedCollaboration"),
  APPROVED_AGG("approved"),
  APPROVED_COLLABORATION_AGG("approvedCollaboration"),
  REJECTED_AGG("rejected"),
  REJECTED_COLLABORATION_AGG("rejectedCollaboration"),
  DISPUTED_AGG("dispute"),
  ASSIGNMENTS_AGG("assignments"),
  EMPTY_FILTER("");

  private final String value;

  QueryFilterType(String value) {
    this.value = value;
  }

  @JsonCreator
  public static QueryFilterType parse(String stringValue) {
    return ParsableEnum.parse(QueryFilterType.class, stringValue);
  }

  @JsonValue
  @Override
  public String getValue() {
    return value;
  }
}
