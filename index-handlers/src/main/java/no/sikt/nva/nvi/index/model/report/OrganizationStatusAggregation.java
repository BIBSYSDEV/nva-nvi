package no.sikt.nva.nvi.index.model.report;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

public record OrganizationStatusAggregation(
    int candidateCount,
    BigDecimal points,
    Map<GlobalApprovalStatus, Integer> globalApprovalStatus,
    Map<ApprovalStatus, Integer> approvalStatus) {

  public OrganizationStatusAggregation {
    if (candidateCount <= 0) {
      throw new IllegalArgumentException("candidateCount must be greater than zero");
    }
    if (points.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("points cannot be negative");
    }
    validateAllEnumValues(GlobalApprovalStatus.class, globalApprovalStatus, "globalApprovalStatus");
    validateAllEnumValues(ApprovalStatus.class, approvalStatus, "approvalStatus");
  }

  private static <E extends Enum<E>> void validateAllEnumValues(
      Class<E> enumClass, Map<E, Integer> map, String fieldName) {
    var missingValues =
        Arrays.stream(enumClass.getEnumConstants()).filter(e -> !map.containsKey(e)).toList();
    if (!missingValues.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " is missing values for: " + missingValues);
    }
  }
}
