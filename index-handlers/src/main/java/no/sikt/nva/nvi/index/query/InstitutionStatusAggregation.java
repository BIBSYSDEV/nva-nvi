package no.sikt.nva.nvi.index.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.common.utils.Validator.shouldNotBeBlank;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.query.ApprovalQuery.approvalBelongsTo;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.filterAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.termsAggregation;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustNotMatch;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ORGANIZATION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ORGANIZATION_SUMMARIES;

import java.util.Map;
import no.sikt.nva.nvi.index.utils.SearchConstants;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class InstitutionStatusAggregation {

  public static final String ORGANIZATION_REPORT_AGGREGATION = "organizations";
  public static final String ORGANIZATION_REPORT_AGGREGATION_TOTALS = "totals";
  public static final String FILTERED_BY_TOP_LEVEL_ORGANIZATION = "filtered_by";
  public static final String AGGREGATED_BY_ORGANIZATION = "by_organization";
  private static final String POINTS = "points";
  private static final String TOTAL = "total";
  private static final String STATUS = "status";
  private static final String GLOBAL_STATUS = "globalStatus";

  private static final String SUMMARY_PATH = jsonPathOf(APPROVALS, ORGANIZATION_SUMMARIES);
  private static final int MAX_SUB_ORGANIZATIONS = 1000;

  private InstitutionStatusAggregation() {}

  public static Aggregation organizationReportAggregation(String topLevelOrganizationId) {
    shouldNotBeBlank(topLevelOrganizationId, "topLevelOrganizationId cannot be blank");
    var organizationAggregation =
        nestedAggregation(
            SUMMARY_PATH, Map.of(AGGREGATED_BY_ORGANIZATION, aggregateByAffiliation()));
    var filterAggregation =
        filterByTopLevelOrganization(
            topLevelOrganizationId,
            Map.of(FILTERED_BY_TOP_LEVEL_ORGANIZATION, organizationAggregation));

    var totalsAggregation = aggregateForTopLevelOrganization(topLevelOrganizationId);

    return nestedAggregation(
        APPROVALS,
        Map.of(
            ORGANIZATION_REPORT_AGGREGATION, filterAggregation,
            ORGANIZATION_REPORT_AGGREGATION_TOTALS, totalsAggregation));
  }

  private static Aggregation aggregateByAffiliation() {
    var organizationAggregations =
        Map.of(
            POINTS, aggregatePotentialAffiliationPoints(),
            STATUS, termsAggregation(SUMMARY_PATH, APPROVAL_STATUS),
            GLOBAL_STATUS, termsAggregation(SUMMARY_PATH, GLOBAL_APPROVAL_STATUS));
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
            STATUS, termsAggregation(APPROVALS, APPROVAL_STATUS),
            POINTS, aggregatePotentialTopLevelPoints(),
            GLOBAL_STATUS, termsAggregation(APPROVALS, GLOBAL_APPROVAL_STATUS));
    return filterByTopLevelOrganization(topLevelOrganizationId, topLevelAggregations);
  }

  public static Aggregation filterByTopLevelOrganization(
      String topLevelOrganizationId, Map<String, Aggregation> subAggregations) {
    return filterAggregation(mustMatch(approvalBelongsTo(topLevelOrganizationId)), subAggregations);
  }

  private static Aggregation aggregatePotentialTopLevelPoints() {
    return filterAggregation(
        mustNotMatch(REJECTED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
        Map.of(TOTAL, sumAggregation(APPROVALS, SearchConstants.POINTS, INSTITUTION_POINTS)));
  }

  private static Aggregation aggregatePotentialAffiliationPoints() {
    return filterAggregation(
        mustNotMatch(REJECTED.getValue(), jsonPathOf(SUMMARY_PATH, APPROVAL_STATUS)),
        Map.of(TOTAL, sumAggregation(SUMMARY_PATH, POINTS)));
  }
}
