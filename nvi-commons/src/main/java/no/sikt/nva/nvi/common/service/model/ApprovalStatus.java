package no.sikt.nva.nvi.common.service.model;

import static java.util.Collections.emptySet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import no.sikt.nva.nvi.common.db.ApprovalStatusDao;

public enum ApprovalStatus {
  APPROVED("Approved"),
  PENDING("Pending"),
  REJECTED("Rejected"),
  NONE("None");

  @JsonValue private final String value;

  ApprovalStatus(String value) {
    this.value = value;
  }

  @JsonCreator
  public static ApprovalStatus parse(String value) {
    return Arrays.stream(values())
        .filter(status -> status.getValue().equalsIgnoreCase(value))
        .findFirst()
        .orElseThrow();
  }

  public String getValue() {
    return value;
  }

  @JsonIgnore
  public Set<ApprovalStatus> getValidTransitions() {
    return switch (this) {
      case APPROVED -> EnumSet.of(PENDING, REJECTED);
      case PENDING -> EnumSet.of(APPROVED, REJECTED);
      case REJECTED -> EnumSet.of(PENDING, APPROVED);
      case NONE -> emptySet();
    };
  }

  @JsonIgnore
  public static ApprovalStatusDao.DbStatus toDbStatus(ApprovalStatus approvalStatus) {
    return switch (approvalStatus) {
      case APPROVED -> ApprovalStatusDao.DbStatus.APPROVED;
      case REJECTED -> ApprovalStatusDao.DbStatus.REJECTED;
      case PENDING, NONE -> ApprovalStatusDao.DbStatus.PENDING;
    };
  }
}
