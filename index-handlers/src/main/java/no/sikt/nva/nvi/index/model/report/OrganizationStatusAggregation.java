package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

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
    if (candidateCount < 0) {
      throw new IllegalArgumentException("candidateCount cannot be negative");
    }

    if (points.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("points cannot be negative");
    }
    points = adjustScaleAndRoundingMode(points);

    validateAllEnumValues(GlobalApprovalStatus.class, globalApprovalStatus, "globalApprovalStatus");
    try {
      globalApprovalStatus = Map.copyOf(globalApprovalStatus);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    validateAllEnumValues(ApprovalStatus.class, approvalStatus, "approvalStatus");
    approvalStatus = Map.copyOf(approvalStatus);
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
