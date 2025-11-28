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
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class InstitutionStatusAggregation {

  public static final String ORGANIZATION_REPORT_AGGREGATION = "organizations";
  public static final String ORGANIZATION_REPORT_AGGREGATION_TOTALS = "totals";
  public static final String FILTERED_BY_TOP_LEVEL_ORGANIZATION = "filtered_by";
  public static final String AGGREGATED_BY_ORGANIZATION = "by_organization";

  private static final String POINTS_AGGREGATION = "points";
  private static final String TOTAL_POINTS_SUM_AGGREGATION = "total";
  private static final String STATUS_AGGREGATION = "status";
  private static final String GLOBAL_STATUS_AGGREGATION = "globalStatus";
  private static final int ORGANIZATION_SUB_UNITS_TERMS_AGGREGATION_SIZE = 1000;

  private InstitutionStatusAggregation() {}

  public static Aggregation topLevelOrganizationStatusAggregation(String topLevelOrganizationId) {
    var organizationAggregation =
        Map.of(
            STATUS_AGGREGATION,
            termsAggregation(APPROVALS, APPROVAL_STATUS).toAggregation(),
            POINTS_AGGREGATION,
            nonRejectedTopLevelPoints(),
            GLOBAL_STATUS_AGGREGATION,
            termsAggregation(APPROVALS, GLOBAL_APPROVAL_STATUS).toAggregation());

    return filterAggregation(
        mustMatch(approvalBelongsTo(topLevelOrganizationId)), organizationAggregation);
  }

  public static Aggregation organizationReportAggregation(String topLevelOrganizationId) {
    shouldNotBeBlank(topLevelOrganizationId, "topLevelOrganizationId cannot be blank");
    var pointsAggregation = nonRejectedAffiliationPoints();
    var statusAggregation =
        termsAggregation(APPROVALS, ORGANIZATION_SUMMARIES, APPROVAL_STATUS).toAggregation();
    var globalStatusAggregation =
        termsAggregation(APPROVALS, ORGANIZATION_SUMMARIES, GLOBAL_APPROVAL_STATUS).toAggregation();

    var organizationAggregation =
        new Aggregation.Builder()
            .terms(
                new TermsAggregation.Builder()
                    .field(jsonPathOf(APPROVALS, ORGANIZATION_SUMMARIES, ORGANIZATION_ID))
                    .size(ORGANIZATION_SUB_UNITS_TERMS_AGGREGATION_SIZE)
                    .build())
            .aggregations(
                Map.of(
                    POINTS_AGGREGATION, pointsAggregation,
                    STATUS_AGGREGATION, statusAggregation,
                    GLOBAL_STATUS_AGGREGATION, globalStatusAggregation))
            .build();
    var nestedOrganizationAggregation =
        new Aggregation.Builder()
            .nested(nestedAggregation(APPROVALS, ORGANIZATION_SUMMARIES))
            .aggregations(Map.of(AGGREGATED_BY_ORGANIZATION, organizationAggregation))
            .build();
    var filterAggregation =
        filterAggregation(
            mustMatch(approvalBelongsTo(topLevelOrganizationId)),
            Map.of(FILTERED_BY_TOP_LEVEL_ORGANIZATION, nestedOrganizationAggregation));

    return new Aggregation.Builder()
        .nested(nestedAggregation(APPROVALS))
        .aggregations(
            Map.of(
                ORGANIZATION_REPORT_AGGREGATION,
                filterAggregation,
                ORGANIZATION_REPORT_AGGREGATION_TOTALS,
                topLevelOrganizationStatusAggregation(topLevelOrganizationId)))
        .build();
  }

  private static Aggregation nonRejectedTopLevelPoints() {
    return filterAggregation(
        mustNotMatch(REJECTED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
        Map.of(
            TOTAL_POINTS_SUM_AGGREGATION, sumAggregation(APPROVALS, POINTS, INSTITUTION_POINTS)));
  }

  private static Aggregation nonRejectedAffiliationPoints() {
    return filterAggregation(
        mustNotMatch(
            REJECTED.getValue(), jsonPathOf(APPROVALS, ORGANIZATION_SUMMARIES, APPROVAL_STATUS)),
        Map.of(
            TOTAL_POINTS_SUM_AGGREGATION,
            sumAggregation(APPROVALS, ORGANIZATION_SUMMARIES, POINTS_AGGREGATION)));
  }
}
