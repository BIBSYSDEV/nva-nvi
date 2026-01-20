package no.sikt.nva.nvi.index.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeBlank;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalBelongsTo;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.filterAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.termsAggregation;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ORGANIZATION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ORGANIZATION_SUMMARIES;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class InstitutionStatusAggregation {

  public static final String AFFILIATION_AGGREGATE = "aggregatedByAffiliation";
  public static final String TOP_LEVEL_AGGREGATE = "aggregatedByTopLevel";
  public static final String FILTERED_BY = "filtered_by";
  public static final String BY_ORGANIZATION = "by_organization";
  public static final String TOTAL = "total";

  private static final String SUMMARY_PATH = jsonPathOf(APPROVALS, ORGANIZATION_SUMMARIES);
  private static final int MAX_SUB_ORGANIZATIONS = 1000;

  private InstitutionStatusAggregation() {}

  /**
   * Aggregation intended to produce summary reports of points and approval statuses. It has two
   * nested sub-aggregations:
   * <li>Summary of top-level totals that include points from all sub-organizations
   * <li>Summary per organization that only includes candidates with a direct affiliation to that
   *     organization
   */
  public static Aggregation organizationReportAggregation(String topLevelOrganizationId) {
    shouldNotBeBlank(topLevelOrganizationId, "topLevelOrganizationId cannot be blank");
    var organizationAggregation =
        nestedAggregation(SUMMARY_PATH, Map.of(BY_ORGANIZATION, aggregateByAffiliation()));
    var filterAggregation =
        filterByTopLevelOrganization(
            topLevelOrganizationId, Map.of(FILTERED_BY, organizationAggregation));

    var totalsAggregation = aggregateForTopLevelOrganization(topLevelOrganizationId);

    return nestedAggregation(
        APPROVALS,
        Map.of(
            AFFILIATION_AGGREGATE, filterAggregation,
            TOP_LEVEL_AGGREGATE, totalsAggregation));
  }

  private static Aggregation aggregateByAffiliation() {
    var organizationAggregations =
        Map.of(
            POINTS,
            aggregatePotentialAffiliationPoints(),
            APPROVAL_STATUS,
            termsAggregation(SUMMARY_PATH, APPROVAL_STATUS),
            GLOBAL_APPROVAL_STATUS,
            termsAggregation(SUMMARY_PATH, GLOBAL_APPROVAL_STATUS));
    var aggregationTerms =
        new TermsAggregation.Builder()
            .field(jsonPathOf(SUMMARY_PATH, ORGANIZATION_ID))
            .size(MAX_SUB_ORGANIZATIONS)
            .build();
    return new Aggregation.Builder()
        .terms(aggregationTerms)
        .aggregations(organizationAggregations)
        .build();
  }

  private static Aggregation aggregateForTopLevelOrganization(String topLevelOrganizationId) {
    var topLevelAggregations =
        Map.of(
            APPROVAL_STATUS,
            termsAggregation(APPROVALS, APPROVAL_STATUS),
            POINTS,
            aggregatePotentialTopLevelPoints(),
            GLOBAL_APPROVAL_STATUS,
            termsAggregation(APPROVALS, GLOBAL_APPROVAL_STATUS));
    return filterByTopLevelOrganization(topLevelOrganizationId, topLevelAggregations);
  }

  public static Aggregation filterByTopLevelOrganization(
      String topLevelOrganizationId, Map<String, Aggregation> subAggregations) {
    return filterAggregation(mustMatch(approvalBelongsTo(topLevelOrganizationId)), subAggregations);
  }

  private static Aggregation aggregatePotentialTopLevelPoints() {
    return filterAggregation(
        mustMatch(
            fieldValueQuery(
                jsonPathOf(APPROVALS, GLOBAL_APPROVAL_STATUS),
                GlobalApprovalStatus.APPROVED.getValue())),
        Map.of(TOTAL, sumAggregation(APPROVALS, POINTS, INSTITUTION_POINTS)));
  }

  private static Aggregation aggregatePotentialAffiliationPoints() {
    return filterAggregation(
        mustMatch(
            fieldValueQuery(
                jsonPathOf(SUMMARY_PATH, GLOBAL_APPROVAL_STATUS),
                GlobalApprovalStatus.APPROVED.getValue())),
        Map.of(TOTAL, sumAggregation(SUMMARY_PATH, POINTS)));
  }
}
