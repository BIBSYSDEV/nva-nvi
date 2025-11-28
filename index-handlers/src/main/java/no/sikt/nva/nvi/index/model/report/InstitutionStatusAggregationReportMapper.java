package no.sikt.nva.nvi.index.model.report;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.AGGREGATED_BY_ORGANIZATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.FILTERED_BY_TOP_LEVEL_ORGANIZATION;
import static no.sikt.nva.nvi.index.query.InstitutionStatusAggregation.ORGANIZATION_REPORT_AGGREGATION;

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
    var nestedAgg = aggregate.nested();
    var aggregations = nestedAgg.aggregations();

    var totals = extractTotals(aggregations, topLevelOrganizationId.toString());
    var byOrganization = extractByOrganization(aggregations);

    return new InstitutionStatusAggregationReport(
        year, topLevelOrganizationId, totals, byOrganization);
  }

  private static OrganizationStatusAggregation createEmptyOrganizationAggregation() {
    return new OrganizationStatusAggregation(
        0, BigDecimal.ZERO, initializeGlobalApprovalStatusMap(), initializeApprovalStatusMap());
  }

  private static OrganizationStatusAggregation extractTotals(
      Map<String, Aggregate> aggregations, String topLevelOrgId) {

    var topLevelOrgAgg = aggregations.get(topLevelOrgId);
    if (isNull(topLevelOrgAgg)) {
      return createEmptyOrganizationAggregation();
    }
    var filterAgg = topLevelOrgAgg.filter();
    if (filterAgg.docCount() == 0) {
      return createEmptyOrganizationAggregation(); // FIXME: Can this happen?
    }
    return extractOrganizationAggregation(filterAgg.aggregations(), (int) filterAgg.docCount());
  }

  private static Map<URI, OrganizationStatusAggregation> extractByOrganization(
      Map<String, Aggregate> aggregations) {

    var subOrgsAgg = aggregations.get(ORGANIZATION_REPORT_AGGREGATION);
    if (isNull(subOrgsAgg)) {
      return Map.of();
    }

    var subOrgsFilter = subOrgsAgg.filter();
    var orgSummariesNestedAgg =
        subOrgsFilter.aggregations().get(FILTERED_BY_TOP_LEVEL_ORGANIZATION);
    if (isNull(orgSummariesNestedAgg)) {
      return Map.of();
    }

    var orgSummariesNested = orgSummariesNestedAgg.nested();
    var byOrgTermsAgg = orgSummariesNested.aggregations().get(AGGREGATED_BY_ORGANIZATION);
    if (isNull(byOrgTermsAgg)) {
      return Map.of();
    }

    var byOrgTerms = byOrgTermsAgg.sterms();

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
