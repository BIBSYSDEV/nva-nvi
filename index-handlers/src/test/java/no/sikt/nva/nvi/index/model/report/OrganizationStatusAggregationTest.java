package no.sikt.nva.nvi.index.model.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.junit.jupiter.api.Test;

class OrganizationStatusAggregationTest {

  @Test
  void shouldThrowOnNegativeCandidateCount() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrganizationStatusAggregation(
                -1, BigDecimal.ONE, validGlobalStatus(), validApprovalStatus()));
  }

  @Test
  void shouldAllowZeroCandidateCount() {
    var aggregation =
        new OrganizationStatusAggregation(
            0, BigDecimal.ZERO, validGlobalStatus(), validApprovalStatus());
    assertEquals(0, aggregation.candidateCount());
  }

  @Test
  void shouldThrowWhenPointsAreNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrganizationStatusAggregation(
                1, BigDecimal.valueOf(-1), validGlobalStatus(), validApprovalStatus()));
  }

  @Test
  void shouldThrowOnMissingValueInStatusMap() {
    var invalidStatusMap = Map.of(ApprovalStatus.PENDING, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrganizationStatusAggregation(
                1, BigDecimal.ONE, validGlobalStatus(), invalidStatusMap));
  }

  @Test
  void shouldThrowOnMissingValueInGlobalStatusMap() {
    var invalidStatusMap = Map.of(GlobalApprovalStatus.APPROVED, 1);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OrganizationStatusAggregation(
                1, BigDecimal.ONE, invalidStatusMap, validApprovalStatus()));
  }

  private static Map<GlobalApprovalStatus, Integer> validGlobalStatus() {
    return Map.of(
        GlobalApprovalStatus.APPROVED, 0,
        GlobalApprovalStatus.PENDING, 1,
        GlobalApprovalStatus.REJECTED, 2,
        GlobalApprovalStatus.DISPUTE, 3);
  }

  private static Map<ApprovalStatus, Integer> validApprovalStatus() {
    return Map.of(
        ApprovalStatus.NEW, 0,
        ApprovalStatus.PENDING, 1,
        ApprovalStatus.APPROVED, 2,
        ApprovalStatus.REJECTED, 3);
  }
}
