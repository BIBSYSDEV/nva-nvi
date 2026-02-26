package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.BY_GLOBAL_STATUS;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.BY_LOCAL_STATUS;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.BY_SECTOR;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.INSTITUTION;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.PER_INSTITUTION;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.POINTS_SUM;

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
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchResponse;

public record AllInstitutionsQuery(NviPeriod period)
    implements ReportAggregationQuery<List<InstitutionAggregationResult>> {

  private static final String REPORTING_PERIOD_YEAR = "reportingPeriod.year";
  private static final String REPORTED = "reported";

  @Override
  public Query query() {
    var yearFilter = yearFilter();
    if (period.isClosed()) {
      var reportedFilter = reportedFilter();
      return new BoolQuery.Builder().filter(yearFilter, reportedFilter).build().toQuery();
    }
    return yearFilter;
  }

  private Query yearFilter() {
    var year = String.valueOf(period.publishingYear());
    return new TermQuery.Builder()
        .field(REPORTING_PERIOD_YEAR)
        .value(v -> v.stringValue(year))
        .build()
        .toQuery();
  }

  private static Query reportedFilter() {
    return new TermQuery.Builder()
        .field(REPORTED)
        .value(v -> v.booleanValue(true))
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
    var sector = parseSector(bucket);
    var byGlobalStatus = parseGlobalStatusBuckets(bucket);
    var undisputed = computeUndisputed(byGlobalStatus);

    return new InstitutionAggregationResult(
        institutionId, period, sector, byGlobalStatus, undisputed);
  }

  private static String parseSector(StringTermsBucket institutionBucket) {
    var sectorBuckets = institutionBucket.aggregations().get(BY_SECTOR).sterms().buckets().array();
    if (sectorBuckets.isEmpty()) {
      return null;
    }
    return sectorBuckets.getFirst().key();
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
      var candidateCount = Math.toIntExact(localBucket.docCount());
      var pointsValue =
          BigDecimal.valueOf(localBucket.aggregations().get(POINTS_SUM).sum().value());
      totalsByStatus.put(localStatus, new CandidateTotal(candidateCount, pointsValue));
    }
    return new LocalStatusSummary(Map.copyOf(totalsByStatus));
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
