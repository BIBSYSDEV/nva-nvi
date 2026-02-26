package no.sikt.nva.nvi.index.report.query;

import java.util.Map;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchResponse;

public sealed interface ReportAggregationQuery<T> permits AllInstitutionsQuery, InstitutionQuery {

  String REPORTING_PERIOD_YEAR = "reportingPeriod.year";
  String REPORTED = "reported";

  Query query();

  Map<String, Aggregation> aggregations();

  T parseResponse(SearchResponse<Void> response);

  static Query yearFilter(NviPeriod period) {
    var year = String.valueOf(period.publishingYear());
    return new TermQuery.Builder()
        .field(REPORTING_PERIOD_YEAR)
        .value(v -> v.stringValue(year))
        .build()
        .toQuery();
  }

  static Query reportedFilter() {
    return new TermQuery.Builder()
        .field(REPORTED)
        .value(v -> v.booleanValue(true))
        .build()
        .toQuery();
  }
}
