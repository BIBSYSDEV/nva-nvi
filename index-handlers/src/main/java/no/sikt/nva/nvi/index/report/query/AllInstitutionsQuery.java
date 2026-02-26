package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.PER_INSTITUTION;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery;
import no.sikt.nva.nvi.index.report.model.CandidateTotal;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import no.sikt.nva.nvi.index.report.model.LocalStatusSummary;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchResponse;

// TODO: Change how we handle reported filtering. Should have a filter to either include everything
// (open/pending period) or only reported results (closed period).
public record AllInstitutionsQuery(NviPeriod period)
    implements ReportAggregationQuery<List<InstitutionAggregationResult>> {

  private static final String REPORTING_PERIOD_YEAR = "reportingPeriod.year";
  private static final String INSTITUTION = "institution";
  private static final String BY_GLOBAL_STATUS = "by_global_status";
  private static final String BY_LOCAL_STATUS = "by_local_status";
  private static final String POINTS = "points";

  // TODO: Build query more cleanly, by composing a filter with each of the aggregation queries in
  // one operation?

  @Override
  public Query query() {
    var year = String.valueOf(period.publishingYear());
    return new TermQuery.Builder()
        .field(REPORTING_PERIOD_YEAR)
        .value(v -> v.stringValue(year))
        .build()
        .toQuery();
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.of(PER_INSTITUTION, AllInstitutionsAggregationQuery.create());
  }

  @Override
  public List<InstitutionAggregationResult> parseResponse(SearchResponse<Void> response) {
    var perInstitution = response.aggregations().get(PER_INSTITUTION);
    var institutionBuckets =
        perInstitution.nested().aggregations().get(INSTITUTION).sterms().buckets().array();
    return institutionBuckets.stream().map(this::parseInstitutionBucket).toList();
  }

  private InstitutionAggregationResult parseInstitutionBucket(StringTermsBucket bucket) {
    var institutionId = URI.create(bucket.key());
    var byGlobalStatus = parseGlobalStatusBuckets(bucket);
    var reportedTotals = computeReportedTotals(byGlobalStatus);
    var undisputed = computeUndisputed(byGlobalStatus);

    // FIXME: Add sector
    return new InstitutionAggregationResult(
        institutionId, period, null, reportedTotals, byGlobalStatus, undisputed);
  }

  private Map<GlobalApprovalStatus, LocalStatusSummary> parseGlobalStatusBuckets(
      StringTermsBucket institutionBucket) {
    var globalBuckets =
        institutionBucket.aggregations().get(BY_GLOBAL_STATUS).sterms().buckets().array();
    var result = new EnumMap<GlobalApprovalStatus, LocalStatusSummary>(GlobalApprovalStatus.class);
    for (var globalBucket : globalBuckets) {
      var globalStatus = GlobalApprovalStatus.parse(globalBucket.key());
      var localStatusSummary = parseLocalStatusBuckets(globalBucket);
      result.put(globalStatus, localStatusSummary);
    }
    return Map.copyOf(result);
  }

  private LocalStatusSummary parseLocalStatusBuckets(StringTermsBucket globalBucket) {
    var localBuckets = globalBucket.aggregations().get(BY_LOCAL_STATUS).sterms().buckets().array();
    var totalsByStatus = new EnumMap<ApprovalStatus, CandidateTotal>(ApprovalStatus.class);
    for (var localBucket : localBuckets) {
      var localStatus = ApprovalStatus.parse(localBucket.key());
      var candidateCount = (int) localBucket.docCount();
      var pointsValue = BigDecimal.valueOf(localBucket.aggregations().get(POINTS).sum().value());
      totalsByStatus.put(localStatus, new CandidateTotal(candidateCount, pointsValue));
    }
    return new LocalStatusSummary(Map.copyOf(totalsByStatus));
  }

  // FIXME: This is wrong
  private CandidateTotal computeReportedTotals(
      Map<GlobalApprovalStatus, LocalStatusSummary> byGlobalStatus) {
    return byGlobalStatus.values().stream()
        .map(LocalStatusSummary::total)
        .reduce(CandidateTotal.ZERO, CandidateTotal::add);
  }

  private LocalStatusSummary computeUndisputed(
      Map<GlobalApprovalStatus, LocalStatusSummary> byGlobalStatus) {
    var undisputedTotals = new EnumMap<ApprovalStatus, CandidateTotal>(ApprovalStatus.class);
    byGlobalStatus.entrySet().stream()
        .filter(entry -> entry.getKey() != GlobalApprovalStatus.DISPUTE)
        .flatMap(entry -> entry.getValue().totalsByStatus().entrySet().stream())
        .forEach(
            entry -> undisputedTotals.merge(entry.getKey(), entry.getValue(), CandidateTotal::add));
    return new LocalStatusSummary(Map.copyOf(undisputedTotals));
  }
}
