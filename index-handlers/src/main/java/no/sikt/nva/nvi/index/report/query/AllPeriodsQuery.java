package no.sikt.nva.nvi.index.report.query;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.FiltersBucket;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public record AllPeriodsQuery(List<NviPeriod> periods)
    implements ReportAggregationQuery<List<PeriodAggregationResult>> {

  private static final String BY_PERIOD = "by_period";

  @Override
  public Query query() {
    return new MatchAllQuery.Builder().build().toQuery();
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.of(BY_PERIOD, buildFiltersAggregation());
  }

  @Override
  public List<PeriodAggregationResult> parseResponse(SearchResponse<Void> response) {
    var keyedBuckets = response.aggregations().get(BY_PERIOD).filters().buckets().keyed();
    return periods.stream().map(period -> parseBucketForPeriod(period, keyedBuckets)).toList();
  }

  private static PeriodAggregationResult parseBucketForPeriod(
      NviPeriod period, Map<String, FiltersBucket> keyedBuckets) {
    var yearKey = String.valueOf(period.publishingYear());
    var bucket = keyedBuckets.get(yearKey);
    return PeriodReportAggregation.parseAggregations(period, bucket.aggregations());
  }

  private Aggregation buildFiltersAggregation() {
    var filtersByPeriod =
        periods.stream()
            .collect(
                Collectors.toMap(
                    period -> String.valueOf(period.publishingYear()), this::periodFilterQuery));
    var subAggregations = Map.ofEntries(PeriodReportAggregation.namedAggregationEntry());
    return new Aggregation.Builder()
        .filters(f -> f.filters(Buckets.of(b -> b.keyed(filtersByPeriod))))
        .aggregations(subAggregations)
        .build();
  }

  private Query periodFilterQuery(NviPeriod period) {
    return new BoolQuery.Builder()
        .filter(ReportAggregationQuery.baseFilters(period))
        .build()
        .toQuery();
  }
}
