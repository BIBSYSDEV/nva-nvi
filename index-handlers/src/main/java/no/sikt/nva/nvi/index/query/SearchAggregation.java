package no.sikt.nva.nvi.index.query;

import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.Aggregations.assignmentsAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.collaborationAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.completedAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.disputeAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.finalizedCollaborationAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.pendingAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.statusAggregation;
import static no.sikt.nva.nvi.index.query.Aggregations.totalCountAggregation;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.organizationReportAggregation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public enum SearchAggregation {
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
  COMPLETED_AGGREGATION_AGG("completed"),
  TOTAL_COUNT_AGGREGATION_AGG("totalCount"),
  ORGANIZATION_APPROVAL_STATUS_AGGREGATION("organizationApprovalStatuses");

  private final String aggregationName;

  SearchAggregation(String aggregationName) {
    this.aggregationName = aggregationName;
  }

  public static SearchAggregation parse(String candidateString) {
    return Arrays.stream(values())
        .filter(type -> type.getAggregationName().equalsIgnoreCase(candidateString))
        .findFirst()
        .orElse(null);
  }

  public static List<SearchAggregation> defaultAggregations() {
    return Stream.of(values())
        .filter(SearchAggregation::isNotOrganizationApprovalStatusAggregation)
        .toList();
  }

  private static boolean isNotOrganizationApprovalStatusAggregation(
      SearchAggregation searchAggregation) {
    return searchAggregation != ORGANIZATION_APPROVAL_STATUS_AGGREGATION;
  }

  public Aggregation generateAggregation(String username, String topLevelCristinOrg) {
    return switch (this) {
      case NEW_AGG -> pendingAggregation(topLevelCristinOrg);
      case NEW_COLLABORATION_AGG -> collaborationAggregation(topLevelCristinOrg, NEW);
      case PENDING_AGG -> statusAggregation(topLevelCristinOrg, PENDING);
      case PENDING_COLLABORATION_AGG -> collaborationAggregation(topLevelCristinOrg, PENDING);
      case APPROVED_AGG -> statusAggregation(topLevelCristinOrg, APPROVED);
      case APPROVED_COLLABORATION_AGG ->
          finalizedCollaborationAggregation(topLevelCristinOrg, APPROVED);
      case REJECTED_AGG -> statusAggregation(topLevelCristinOrg, REJECTED);
      case REJECTED_COLLABORATION_AGG ->
          finalizedCollaborationAggregation(topLevelCristinOrg, REJECTED);
      case DISPUTED_AGG -> disputeAggregation(topLevelCristinOrg);
      case ASSIGNMENTS_AGG -> assignmentsAggregation(username, topLevelCristinOrg);
      case COMPLETED_AGGREGATION_AGG -> completedAggregation(topLevelCristinOrg);
      case TOTAL_COUNT_AGGREGATION_AGG -> totalCountAggregation(topLevelCristinOrg);
      case ORGANIZATION_APPROVAL_STATUS_AGGREGATION ->
          organizationReportAggregation(topLevelCristinOrg);
    };
  }

  public String getAggregationName() {
    return aggregationName;
  }
}
