package no.sikt.nva.nvi.index.query;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeBlank;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalBelongsTo;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalStatusIs;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.filterAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.termsAggregation;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.assignmentsQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.containsNonFinalizedStatusQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.matchAtLeastOne;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.multipleApprovalsQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustNotMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.notDisputeQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.statusQuery;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INVOLVED_ORGS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class Aggregations {

  public static final String APPROVAL_ORGANIZATIONS_AGGREGATION = "organizations";
  private static final String INSTITUTION_ID_PATH = jsonPathOf(APPROVALS, INSTITUTION_ID);
  private static final String DISPUTE_AGGREGATION = "dispute";
  private static final String POINTS_AGGREGATION = "points";
  private static final String DISPUTE = "Dispute";
  private static final String TOTAL_POINTS_SUM_AGGREGATION = "total";
  private static final String ALL_AGGREGATIONS = "all";
  private static final String STATUS_AGGREGATION = "status";
  private static final int ORGANIZATION_SUB_UNITS_TERMS_AGGREGATION_SIZE = 1000;

  private Aggregations() {}

  public static Map<String, Aggregation> generateAggregations(
      String aggregationType, String username, String topLevelCristinOrg) {
    return aggregationTypeIsNotSpecified(aggregationType)
        ? generateAllDefaultAggregationTypes(username, topLevelCristinOrg)
        : generateSingleAggregation(aggregationType, username, topLevelCristinOrg);
  }

  public static Aggregation organizationApprovalStatusAggregationsOld(String topLevelCristinOrg) {
    var statusAggregation = termsAggregation(APPROVALS, APPROVAL_STATUS).toAggregation();
    var pointAggregation = filterNotRejectedPointsAggregation();
    var disputeAggregation = filterStatusDisputeAggregation();
    var organizationAggregation =
        new Aggregation.Builder()
            .terms(
                new TermsAggregation.Builder()
                    .field(jsonPathOf(APPROVALS, INVOLVED_ORGS))
                    .size(ORGANIZATION_SUB_UNITS_TERMS_AGGREGATION_SIZE)
                    .build())
            .aggregations(
                Map.of(
                    STATUS_AGGREGATION, statusAggregation,
                    POINTS_AGGREGATION, pointAggregation,
                    DISPUTE_AGGREGATION, disputeAggregation))
            .build();
    var filterAggregation =
        filterAggregation(
            mustMatch(approvalInstitutionIdQuery(topLevelCristinOrg)),
            Map.of(APPROVAL_ORGANIZATIONS_AGGREGATION, organizationAggregation));

    return new Aggregation.Builder()
        .nested(nestedAggregation(APPROVALS))
        .aggregations(
            isNull(topLevelCristinOrg) ? Map.of() : Map.of(topLevelCristinOrg, filterAggregation))
        .build();
  }

  public static Aggregation organizationApprovalStatusAggregations(String topLevelOrganizationId) {
    shouldNotBeBlank(topLevelOrganizationId, "topLevelOrganizationId cannot be blank");

    var pointsAggregation = filterNotRejectedOrganizationPointsAggregation();
    var statusAggregation =
        termsAggregation(APPROVALS, "organizationSummaries", APPROVAL_STATUS).toAggregation();
    var globalStatusAggregation =
        termsAggregation(APPROVALS, "organizationSummaries", "globalApprovalStatus")
            .toAggregation();

    var organizationAggregation =
        new Aggregation.Builder()
            .terms(
                new TermsAggregation.Builder()
                    .field(jsonPathOf(APPROVALS, "organizationSummaries", "organizationId"))
                    .size(ORGANIZATION_SUB_UNITS_TERMS_AGGREGATION_SIZE)
                    .build())
            .aggregations(
                Map.of(
                    POINTS_AGGREGATION,
                    pointsAggregation,
                    STATUS_AGGREGATION,
                    statusAggregation,
                    "globalStatus",
                    globalStatusAggregation))
            .build();
    var nestedOrganizationAggregation =
        new Aggregation.Builder()
            .nested(nestedAggregation(APPROVALS, "organizationSummaries"))
            .aggregations(Map.of("by_organization", organizationAggregation))
            .build();
    var filterAggregation =
        filterAggregation(
            mustMatch(approvalInstitutionIdQuery(topLevelOrganizationId)),
            Map.of("org_summaries_nested", nestedOrganizationAggregation));

    return new Aggregation.Builder()
        .nested(nestedAggregation(APPROVALS))
        .aggregations(Map.of(topLevelOrganizationId, filterAggregation))
        .build();
  }

  public static Aggregation totalCountAggregation(String topLevelCristinOrg) {
    final var fieldValueQuery = approvalInstitutionIdQuery(topLevelCristinOrg);
    final var query = mustMatch(fieldValueQuery);
    return filterAggregation(nestedQuery(APPROVALS, query));
  }

  public static Aggregation statusAggregation(String topLevelCristinOrg, ApprovalStatus status) {
    return filterAggregation(mustMatch(statusQuery(topLevelCristinOrg, status), notDisputeQuery()));
  }

  public static Aggregation pendingAggregation(String topLevelOrganization) {
    return filterAggregation(
        mustMatch(
            notDisputeQuery(),
            nestedQuery(
                APPROVALS,
                approvalBelongsTo(topLevelOrganization),
                matchAtLeastOne(approvalStatusIs(NEW), approvalStatusIs(PENDING)))));
  }

  public static Aggregation completedAggregation(String topLevelOrganization) {
    return filterAggregation(
        nestedQuery(
            APPROVALS,
            approvalBelongsTo(topLevelOrganization),
            matchAtLeastOne(approvalStatusIs(APPROVED), approvalStatusIs(REJECTED))));
  }

  public static Aggregation assignmentsAggregation(String username, String topLevelCristinOrg) {
    return filterAggregation(assignmentsQuery(username, topLevelCristinOrg));
  }

  public static Aggregation finalizedCollaborationAggregation(
      String topLevelCristinOrg, ApprovalStatus status) {
    return filterAggregation(
        mustMatch(
            statusQuery(topLevelCristinOrg, status),
            containsNonFinalizedStatusQuery(),
            multipleApprovalsQuery()));
  }

  public static Aggregation collaborationAggregation(
      String topLevelCristinOrg, ApprovalStatus status) {
    return filterAggregation(
        mustMatch(
            statusQuery(topLevelCristinOrg, status), multipleApprovalsQuery(), notDisputeQuery()));
  }

  public static Aggregation disputeAggregation(String topLevelCristinOrg) {
    return filterAggregation(mustMatch(globalStatusDisputeForInstitution(topLevelCristinOrg)));
  }

  private static Aggregation filterNotRejectedPointsAggregation() {
    return filterAggregation(
        mustNotMatch(REJECTED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
        Map.of(
            TOTAL_POINTS_SUM_AGGREGATION, sumAggregation(APPROVALS, POINTS, INSTITUTION_POINTS)));
  }

  private static Aggregation filterNotRejectedOrganizationPointsAggregation() {
    return filterAggregation(
        mustNotMatch(
            REJECTED.getValue(), jsonPathOf(APPROVALS, "organizationSummaries", APPROVAL_STATUS)),
        Map.of(
            TOTAL_POINTS_SUM_AGGREGATION,
            sumAggregation(APPROVALS, "organizationSummaries", "points")));
  }

  private static Aggregation filterStatusDisputeAggregation() {
    return filterAggregation(
        mustMatch(fieldValueQuery(jsonPathOf(APPROVALS, GLOBAL_APPROVAL_STATUS), DISPUTE)));
  }

  private static Query approvalInstitutionIdQuery(String topLevelCristinOrg) {
    return fieldValueQuery(INSTITUTION_ID_PATH, topLevelCristinOrg);
  }

  private static Query[] globalStatusDisputeForInstitution(String institutionId) {
    return new Query[] {
      approvalInstitutionIdQuery(institutionId),
      fieldValueQuery(GLOBAL_APPROVAL_STATUS, GlobalApprovalStatus.DISPUTE.getValue())
    };
  }

  private static boolean aggregationTypeIsNotSpecified(String aggregationType) {
    return isNull(aggregationType) || ALL_AGGREGATIONS.equals(aggregationType);
  }

  private static Map<String, Aggregation> generateSingleAggregation(
      String aggregationType, String username, String topLevelCristinOrg) {
    var aggregation = SearchAggregation.parse(aggregationType);
    var aggregations = new HashMap<String, Aggregation>();
    addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
    return aggregations;
  }

  private static Map<String, Aggregation> generateAllDefaultAggregationTypes(
      String username, String topLevelCristinOrg) {
    var aggregations = new HashMap<String, Aggregation>();
    for (var aggregation : SearchAggregation.defaultAggregations()) {
      addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
    }
    return aggregations;
  }

  private static void addAggregation(
      String username,
      String topLevelCristinOrg,
      Map<String, Aggregation> aggregations,
      SearchAggregation aggregation) {
    aggregations.put(
        aggregation.getAggregationName(),
        aggregation.generateAggregation(username, topLevelCristinOrg));
  }
}
