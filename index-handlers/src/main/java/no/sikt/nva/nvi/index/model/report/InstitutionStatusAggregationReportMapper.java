package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.AGGREGATED_BY_ORGANIZATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.FILTERED_BY_TOP_LEVEL_ORGANIZATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.ORGANIZATION_REPORT_AGGREGATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.ORGANIZATION_REPORT_AGGREGATION_TOTALS;

import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

/** This mapper converts the raw aggregation output from OpenSearch to a simpler data class. */
public final class InstitutionStatusAggregationReportMapper {

  private static final String POINTS_AGG = "points";
  private static final String TOTAL_AGG = "total";
  private static final String GLOBAL_STATUS_AGG = "globalStatus";
  private static final String STATUS_AGG = "status";

  private InstitutionStatusAggregationReportMapper() {}

  public static InstitutionStatusAggregationReport fromAggregation(
      Aggregate aggregate, String year, URI topLevelOrganizationId) {
    return new InstitutionStatusAggregationReport(
        year, topLevelOrganizationId, extractTotals(aggregate), extractByOrganization(aggregate));
  }

  private static OrganizationStatusAggregation extractTotals(Aggregate aggregate) {
    var totalsAggregate =
        aggregate.nested().aggregations().get(ORGANIZATION_REPORT_AGGREGATION_TOTALS).filter();

    return extractOrganizationAggregation(
        totalsAggregate.aggregations(), (int) totalsAggregate.docCount());
  }

  private static Map<URI, OrganizationStatusAggregation> extractByOrganization(
      Aggregate aggregate) {

    var byOrgTerms =
        aggregate
            .nested()
            .aggregations()
            .get(ORGANIZATION_REPORT_AGGREGATION)
            .filter()
            .aggregations()
            .get(FILTERED_BY_TOP_LEVEL_ORGANIZATION)
            .nested()
            .aggregations()
            .get(AGGREGATED_BY_ORGANIZATION)
            .sterms();

    var result = new LinkedHashMap<URI, OrganizationStatusAggregation>();
    for (var bucket : byOrgTerms.buckets().array()) {
      var orgId = URI.create(bucket.key());
      var orgAggregation =
          extractOrganizationAggregation(bucket.aggregations(), (int) bucket.docCount());
      result.put(orgId, orgAggregation);
    }
    return result;
  }

  private static OrganizationStatusAggregation extractOrganizationAggregation(
      Map<String, Aggregate> aggregations, int candidateCount) {

    var points = extractPoints(aggregations);
    var globalApprovalStatus = extractGlobalApprovalStatusCounts(aggregations);
    var approvalStatus = extractApprovalStatusCounts(aggregations);

    return new OrganizationStatusAggregation(
        candidateCount, points, globalApprovalStatus, approvalStatus);
  }

  private static BigDecimal extractPoints(Map<String, Aggregate> aggregations) {
    var pointsFilter = aggregations.get(POINTS_AGG).filter();
    var sumAgg = pointsFilter.aggregations().get(TOTAL_AGG).sum();
    return BigDecimal.valueOf(sumAgg.value());
  }

  private static Map<GlobalApprovalStatus, Integer> extractGlobalApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {

    var termsAgg = aggregations.get(GLOBAL_STATUS_AGG).sterms();
    var result = initializeGlobalApprovalStatusMap();

    for (var bucket : termsAgg.buckets().array()) {
      var status = GlobalApprovalStatus.parse(bucket.key());
      result.put(status, (int) bucket.docCount());
    }
    return result;
  }

  private static Map<ApprovalStatus, Integer> extractApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {

    var termsAgg = aggregations.get(STATUS_AGG).sterms();
    var result = initializeApprovalStatusMap();

    for (var bucket : termsAgg.buckets().array()) {
      var status = ApprovalStatus.parse(bucket.key());
      result.put(status, (int) bucket.docCount());
    }
    return result;
  }

  private static Map<GlobalApprovalStatus, Integer> initializeGlobalApprovalStatusMap() {
    var map = new EnumMap<GlobalApprovalStatus, Integer>(GlobalApprovalStatus.class);
    for (var status : GlobalApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }

  private static Map<ApprovalStatus, Integer> initializeApprovalStatusMap() {
    var map = new EnumMap<ApprovalStatus, Integer>(ApprovalStatus.class);
    for (var status : ApprovalStatus.values()) {
      map.put(status, 0);
    }
    return map;
  }
}
