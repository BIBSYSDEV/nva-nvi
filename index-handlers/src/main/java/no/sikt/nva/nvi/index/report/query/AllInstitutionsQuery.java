package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.index.report.query.InstitutionReportAggregation.PER_INSTITUTION;

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
    var yearFilter = ReportAggregationQuery.yearFilter(period);
    if (period.isClosed()) {
      var reportedFilter = ReportAggregationQuery.reportedFilter();
      return new BoolQuery.Builder().filter(yearFilter, reportedFilter).build().toQuery();
    }
    return yearFilter;
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.of(PER_INSTITUTION, InstitutionReportAggregation.aggregation());
  }

  @Override
  public List<InstitutionAggregationResult> parseResponse(SearchResponse<Void> response) {
    return new InstitutionReportAggregation(period).parseResponse(response);
  }
}
