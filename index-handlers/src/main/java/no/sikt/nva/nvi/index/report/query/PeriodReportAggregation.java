package no.sikt.nva.nvi.index.report.query;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.termsAggregationWithSubAggregations;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.model.CandidateTotal;
import no.sikt.nva.nvi.index.report.model.PeriodAggregationResult;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch.core.SearchResponse;

public final class PeriodReportAggregation {

  private static final String BY_GLOBAL_STATUS = "by_global_status";
  private static final String POINTS_SUM = "points";

  private PeriodReportAggregation() {}

  /**
   * Returns a named aggregation entry for use in an OpenSearch search request. The aggregation
   * groups candidates by global approval status, summing points at each level. Use {@link
   * #parseResponse} to extract results from the search response.
   */
  public static Map.Entry<String, Aggregation> namedAggregationEntry() {
    return Map.entry(BY_GLOBAL_STATUS, createByGlobalStatusAggregation());
  }

  public static PeriodAggregationResult parseResponse(
      NviPeriod period, SearchResponse<Void> response) {
    var globalStatusBuckets =
        response.aggregations().get(BY_GLOBAL_STATUS).sterms().buckets().array();
    var byGlobalStatus =
        new EnumMap<GlobalApprovalStatus, CandidateTotal>(GlobalApprovalStatus.class);
    for (var bucket : globalStatusBuckets) {
      var status = GlobalApprovalStatus.parse(bucket.key());
      var candidateCount = Math.toIntExact(bucket.docCount());
      var points = getPointsValue(bucket);
      byGlobalStatus.put(status, new CandidateTotal(candidateCount, points));
    }
    return new PeriodAggregationResult(period, byGlobalStatus);
  }

  private static Aggregation createByGlobalStatusAggregation() {
    return termsAggregationWithSubAggregations(
        GLOBAL_APPROVAL_STATUS, Map.of(POINTS_SUM, sumAggregation(POINTS)));
  }

  private static BigDecimal getPointsValue(StringTermsBucket bucket) {
    var aggregatedPoints = bucket.aggregations().get(POINTS_SUM).sum();
    return nonNull(aggregatedPoints.value())
        ? BigDecimal.valueOf(aggregatedPoints.value())
        : BigDecimal.ZERO;
  }
}
