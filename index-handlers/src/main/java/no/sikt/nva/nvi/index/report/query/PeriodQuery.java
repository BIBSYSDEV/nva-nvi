package no.sikt.nva.nvi.index.report.query;

import java.util.Map;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public record PeriodQuery(NviPeriod period)
    implements ReportAggregationQuery<PeriodAggregationResult> {

  @Override
  public Query query() {
    return new BoolQuery.Builder()
        .filter(ReportAggregationQuery.baseFilters(period))
        .build()
        .toQuery();
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.ofEntries(PeriodReportAggregation.namedAggregationEntry());
  }

  @Override
  public PeriodAggregationResult parseResponse(SearchResponse<Void> response) {
    return PeriodReportAggregation.parseAggregations(period, response.aggregations());
  }
}
