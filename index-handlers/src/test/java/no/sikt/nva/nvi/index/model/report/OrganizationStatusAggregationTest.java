package no.sikt.nva.nvi.index.model.report;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OrganizationStatusAggregationTest {

  private static final String AGGREGATION_FACTORIES = "aggregationFactories";

  @FunctionalInterface
  interface AggregationFactory {
    OrganizationStatusAggregation create(
        int candidateCount,
        BigDecimal points,
        Map<GlobalApprovalStatus, Integer> globalApprovalStatus,
        Map<ApprovalStatus, Integer> approvalStatus);
  }

  static Stream<AggregationFactory> aggregationFactories() {
    return Stream.of(TopLevelAggregation::new, DirectAffiliationAggregation::new);
  }

  @ParameterizedTest
  @MethodSource(AGGREGATION_FACTORIES)
  void shouldThrowOnNegativeCandidateCount(AggregationFactory factory) {
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create(-1, BigDecimal.ONE, validGlobalStatus(), validApprovalStatus()));
  }

  @ParameterizedTest
  @MethodSource(AGGREGATION_FACTORIES)
  void shouldThrowWhenPointsAreNegative(AggregationFactory factory) {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            factory.create(1, BigDecimal.valueOf(-1), validGlobalStatus(), validApprovalStatus()));
  }

  @ParameterizedTest
  @MethodSource(AGGREGATION_FACTORIES)
  void shouldThrowOnMissingValueInApprovalStatusMap(AggregationFactory factory) {
    var incompleteMap = Map.of(ApprovalStatus.PENDING, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create(1, BigDecimal.ONE, validGlobalStatus(), incompleteMap));
  }

  @ParameterizedTest
  @MethodSource(AGGREGATION_FACTORIES)
  void shouldThrowOnMissingValueInGlobalApprovalStatusMap(AggregationFactory factory) {
    var incompleteMap = Map.of(GlobalApprovalStatus.APPROVED, 1);
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.create(1, BigDecimal.ONE, incompleteMap, validApprovalStatus()));
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
