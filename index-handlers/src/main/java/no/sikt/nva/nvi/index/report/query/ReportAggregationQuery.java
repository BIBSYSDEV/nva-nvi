package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchResponse;

public sealed interface ReportAggregationQuery<T>
    permits AllInstitutionsQuery, InstitutionQuery, PeriodQuery {

  String REPORTING_PERIOD_YEAR = "reportingPeriod.year";

  Query query();

  Map<String, Aggregation> aggregations();

  T parseResponse(SearchResponse<Void> response);

  static List<Query> baseFilters(NviPeriod period) {
    var filters = new ArrayList<Query>();
    filters.add(yearFilter(period));
    if (period.isClosed()) {
      filters.add(approvedFilter());
    }
    return filters;
  }

  static Query yearFilter(NviPeriod period) {
    var year = String.valueOf(period.publishingYear());
    return new TermQuery.Builder()
        .field(REPORTING_PERIOD_YEAR)
        .value(v -> v.stringValue(year))
        .build()
        .toQuery();
  }

  static Query approvedFilter() {
    return new TermQuery.Builder()
        .field(GLOBAL_APPROVAL_STATUS)
        .value(v -> v.stringValue(GlobalApprovalStatus.APPROVED.getValue()))
        .build()
        .toQuery();
  }
}
