package no.sikt.nva.nvi.index.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.organizationReportAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.filterAggregation;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;

import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public enum SearchAggregation {
  TOTAL_COUNT_AGGREGATION_AGG("totalCount"),
  ORGANIZATION_APPROVAL_STATUS_AGGREGATION("organizationApprovalStatuses");

  private final String aggregationName;

  SearchAggregation(String aggregationName) {
    this.aggregationName = aggregationName;
  }

  public Aggregation generateAggregation(String topLevelCristinOrg) {
    return switch (this) {
      case TOTAL_COUNT_AGGREGATION_AGG -> totalCountAggregation(topLevelCristinOrg);
      case ORGANIZATION_APPROVAL_STATUS_AGGREGATION ->
          organizationReportAggregation(topLevelCristinOrg);
    };
  }

  public String getAggregationName() {
    return aggregationName;
  }

  private static Aggregation totalCountAggregation(String topLevelCristinOrg) {
    var institutionIdQuery =
        fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), topLevelCristinOrg);
    return filterAggregation(nestedQuery(APPROVALS, mustMatch(institutionIdQuery)));
  }
}
