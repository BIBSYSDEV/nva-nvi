package no.sikt.nva.nvi.index.query;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum QueryFilterType {
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

  private final String filter;

  QueryFilterType(String filter) {
    this.filter = filter;
  }

  public static Optional<QueryFilterType> parse(String candidate) {
    var testValue = isNull(candidate) ? "" : candidate;
    return Arrays.stream(values())
        .filter(item -> item.getFilter().equalsIgnoreCase(testValue))
        .findAny();
  }

  @JsonValue
  public String getFilter() {
    return filter;
  }
}
