package no.sikt.nva.nvi.index.report.query;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public record AllInstitutionsQuery(NviPeriod period)
    implements ReportAggregationQuery<List<InstitutionAggregationResult>> {

  @Override
  public Query query() {
    return new BoolQuery.Builder()
        .filter(ReportAggregationQuery.baseFilters(period))
        .build()
        .toQuery();
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.ofEntries(InstitutionReportAggregation.perInstitutionAggregation());
  }

  @Override
  public List<InstitutionAggregationResult> parseResponse(SearchResponse<Void> response) {
    return InstitutionReportAggregation.parseResponse(period, response);
  }
}
