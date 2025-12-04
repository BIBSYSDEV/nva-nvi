package no.sikt.nva.nvi.index.model.report;

import static java.util.stream.Collectors.toMap;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.AFFILIATION_AGGREGATE;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.BY_ORGANIZATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.FILTERED_BY;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.TOP_LEVEL_AGGREGATE;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.TOTAL;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

/** This mapper converts the raw aggregation output from OpenSearch to a simpler data class. */
public final class InstitutionStatusAggregationReportMapper {
  private static final int INITIAL_STATUS_COUNT = 0;

  private InstitutionStatusAggregationReportMapper() {}

  public static InstitutionStatusAggregationReport fromAggregation(
      Aggregate aggregate, String year, URI topLevelOrganizationId) {
    return new InstitutionStatusAggregationReport(
        year, topLevelOrganizationId, extractTotals(aggregate), extractByOrganization(aggregate));
  }

  private static TopLevelAggregation extractTotals(Aggregate aggregate) {
    var aggregatedByTopLevel = aggregate.nested().aggregations().get(TOP_LEVEL_AGGREGATE).filter();
    var subAggregations = aggregatedByTopLevel.aggregations();

    var candidateCount = (int) aggregatedByTopLevel.docCount();
    var points = extractPoints(subAggregations);
    var globalApprovalStatus = extractGlobalApprovalStatusCounts(subAggregations);
    var approvalStatus = extractApprovalStatusCounts(subAggregations);

    return new TopLevelAggregation(candidateCount, points, globalApprovalStatus, approvalStatus);
  }

  private static Map<URI, DirectAffiliationAggregation> extractByOrganization(Aggregate aggregate) {
    var organizationBuckets =
        aggregate
            .nested()
            .aggregations()
            .get(AFFILIATION_AGGREGATE)
            .filter()
            .aggregations()
            .get(FILTERED_BY)
            .nested()
            .aggregations()
            .get(BY_ORGANIZATION)
            .sterms()
            .buckets()
            .array();

    var aggregationMap =
        organizationBuckets.stream()
            .collect(
                toMap(
                    InstitutionStatusAggregationReportMapper::uriFromKey,
                    InstitutionStatusAggregationReportMapper::extractDirectAffiliationAggregation));

    return Map.copyOf(aggregationMap);
  }

  private static URI uriFromKey(StringTermsBucket bucket) {
    return URI.create(bucket.key());
  }

  private static DirectAffiliationAggregation extractDirectAffiliationAggregation(
      StringTermsBucket bucket) {
    var candidateCount = (int) bucket.docCount();
    var points = extractPoints(bucket.aggregations());
    var globalApprovalStatus = extractGlobalApprovalStatusCounts(bucket.aggregations());
    var approvalStatus = extractApprovalStatusCounts(bucket.aggregations());

    return new DirectAffiliationAggregation(
        candidateCount, points, globalApprovalStatus, approvalStatus);
  }

  private static BigDecimal extractPoints(Map<String, Aggregate> aggregations) {
    var pointsAggregate = aggregations.get(POINTS).filter();
    var totalPoints = pointsAggregate.aggregations().get(TOTAL).sum();
    return BigDecimal.valueOf(totalPoints.value());
  }

  private static Map<GlobalApprovalStatus, Integer> extractGlobalApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {
    var buckets = aggregations.get(GLOBAL_APPROVAL_STATUS).sterms().buckets().array();
    var result = enumCounterMap(GlobalApprovalStatus.class);

    for (var bucket : buckets) {
      var status = GlobalApprovalStatus.parse(bucket.key());
      result.put(status, (int) bucket.docCount());
    }
    return Map.copyOf(result);
  }

  private static Map<ApprovalStatus, Integer> extractApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {
    var buckets = aggregations.get(APPROVAL_STATUS).sterms().buckets().array();
    var result = enumCounterMap(ApprovalStatus.class);

    for (var bucket : buckets) {
      var status = ApprovalStatus.parse(bucket.key());
      result.put(status, (int) bucket.docCount());
    }
    return Map.copyOf(result);
  }

  private static <E extends Enum<E>> Map<E, Integer> enumCounterMap(Class<E> enumClass) {
    var map = new EnumMap<E, Integer>(enumClass);
    for (var value : enumClass.getEnumConstants()) {
      map.put(value, INITIAL_STATUS_COUNT);
    }
    return map;
  }
}
