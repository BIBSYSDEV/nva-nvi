package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalView;
import no.sikt.nva.nvi.index.report.model.CandidateTotal;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import no.sikt.nva.nvi.index.report.model.LocalStatusSummary;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregation;
import org.opensearch.client.opensearch.core.SearchResponse;

public final class InstitutionReportAggregation {

  public static final String PER_INSTITUTION = "per_institution";
  public static final String INSTITUTION = "institution";
  public static final String BY_GLOBAL_STATUS = "by_global_status";
  public static final String INSTITUTION_DETAILS = "institution_details";
  public static final String BY_LOCAL_STATUS = "by_local_status";
  public static final String POINTS_SUM = "points";
  private static final int MAX_INSTITUTIONS = 500;

  private final NviPeriod period;

  public InstitutionReportAggregation(NviPeriod period) {
    this.period = period;
  }

  public static Aggregation aggregation() {
    var pointsSum = sumAggregation(APPROVALS, POINTS, INSTITUTION_POINTS);

    var byLocalStatus =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, APPROVAL_STATUS), Map.of(POINTS_SUM, pointsSum));

    var byGlobalStatus =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, GLOBAL_APPROVAL_STATUS), Map.of(BY_LOCAL_STATUS, byLocalStatus));

    var institutionDetails =
        new Aggregation.Builder().topHits(new TopHitsAggregation.Builder().size(1).build()).build();

    var institutionAggregation =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, INSTITUTION_ID),
            MAX_INSTITUTIONS,
            Map.of(
                BY_GLOBAL_STATUS, byGlobalStatus,
                INSTITUTION_DETAILS, institutionDetails));

    return nestedAggregation(APPROVALS, Map.of(INSTITUTION, institutionAggregation));
  }

  public List<InstitutionAggregationResult> parseResponse(SearchResponse<Void> response) {
    var perInstitution = response.aggregations().get(PER_INSTITUTION);
    var institutionBuckets =
        perInstitution.nested().aggregations().get(INSTITUTION).sterms().buckets().array();
    return institutionBuckets.stream().map(this::parseInstitutionBucket).toList();
  }

  private InstitutionAggregationResult parseInstitutionBucket(StringTermsBucket bucket) {
    var institutionId = URI.create(bucket.key());
    var details = parseInstitutionDetails(bucket);
    var byGlobalStatus = parseGlobalStatusBuckets(bucket);
    var undisputed = computeUndisputed(byGlobalStatus);

    return new InstitutionAggregationResult(
        institutionId, period, details.sector(), details.labels(), byGlobalStatus, undisputed);
  }

  private static ApprovalView parseInstitutionDetails(StringTermsBucket institutionBucket) {
    return institutionBucket
        .aggregations()
        .get(INSTITUTION_DETAILS)
        .topHits()
        .hits()
        .hits()
        .getFirst()
        .source()
        .to(ApprovalView.class);
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

  private static Aggregation termsAggregationWithSubs(
      String field, Map<String, Aggregation> subAggregations) {
    return new Aggregation.Builder()
        .terms(new TermsAggregation.Builder().field(field).build())
        .aggregations(subAggregations)
        .build();
  }

  private static Aggregation termsAggregationWithSubs(
      String field, int size, Map<String, Aggregation> subAggregations) {
    return new Aggregation.Builder()
        .terms(new TermsAggregation.Builder().field(field).size(size).build())
        .aggregations(subAggregations)
        .build();
  }
}
