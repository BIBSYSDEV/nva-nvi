package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.common.utils.DecimalUtils.adjustScaleAndRoundingMode;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "TopLevelAggregation", value = TopLevelAggregation.class),
  @JsonSubTypes.Type(
      name = "DirectAffiliationAggregation",
      value = DirectAffiliationAggregation.class)
})
public sealed interface OrganizationStatusAggregation
    permits TopLevelAggregation, DirectAffiliationAggregation {

  int candidateCount();

  BigDecimal points();

  Map<GlobalApprovalStatus, Integer> globalApprovalStatus();

  Map<ApprovalStatus, Integer> approvalStatus();

  static BigDecimal validateAndAdjustPoints(BigDecimal points) {
    if (points.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("points cannot be negative");
    }
    return adjustScaleAndRoundingMode(points);
  }

  static void validateCandidateCount(int candidateCount) {
    if (candidateCount < 0) {
      throw new IllegalArgumentException("candidateCount cannot be negative");
    }
  }

  static <E extends Enum<E>> Map<E, Integer> validateAndCopyStatusMap(
      Class<E> enumClass, Map<E, Integer> map, String fieldName) {
    var missingValues =
        Arrays.stream(enumClass.getEnumConstants()).filter(e -> !map.containsKey(e)).toList();
    if (!missingValues.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " is missing values for: " + missingValues);
    }
    return Map.copyOf(map);
  }
}
