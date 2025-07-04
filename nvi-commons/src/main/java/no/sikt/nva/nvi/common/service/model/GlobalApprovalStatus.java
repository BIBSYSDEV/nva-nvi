package no.sikt.nva.nvi.common.service.model;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import nva.commons.core.SingletonCollector;

public enum GlobalApprovalStatus {
  APPROVED("Approved"),
  PENDING("Pending"),
  REJECTED("Rejected"),
  DISPUTE("Dispute");

  private final String value;

  GlobalApprovalStatus(String value) {
    this.value = value;
  }

  public static GlobalApprovalStatus fromValue(String candidate) {
    return Arrays.stream(values())
        .filter(item -> item.getValue().equalsIgnoreCase(candidate))
        .collect(SingletonCollector.tryCollect())
        .orElseThrow();
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
