package no.sikt.nva.nvi.index.model.report;

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
import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

/** This mapper converts the raw aggregation output from OpenSearch to a simpler data class. */
public final class InstitutionStatusAggregationReportMapper {

  private InstitutionStatusAggregationReportMapper() {}

  public static InstitutionStatusAggregationReport fromAggregation(
      Aggregate aggregate, String year, URI topLevelOrganizationId) {
    return new InstitutionStatusAggregationReport(
        year, topLevelOrganizationId, extractTotals(aggregate), extractByOrganization(aggregate));
  }

  private static OrganizationStatusAggregation extractTotals(Aggregate aggregate) {
    var totalsAggregate = aggregate.nested().aggregations().get(TOP_LEVEL_AGGREGATE).filter();

    return extractOrganizationAggregation(
        totalsAggregate.aggregations(), (int) totalsAggregate.docCount());
  }

  private static Map<URI, OrganizationStatusAggregation> extractByOrganization(
      Aggregate aggregate) {
    var summariesByOrganization =
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
            .sterms();

    var result = new HashMap<URI, OrganizationStatusAggregation>();
    for (var bucket : summariesByOrganization.buckets().array()) {
      var organizationId = URI.create(bucket.key());
      var organizationSummary =
          extractOrganizationAggregation(bucket.aggregations(), (int) bucket.docCount());
      result.put(organizationId, organizationSummary);
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
    var pointsAggregate = aggregations.get(POINTS).filter();
    var totalPoints = pointsAggregate.aggregations().get(TOTAL).sum();
    return BigDecimal.valueOf(totalPoints.value());
  }

  private static Map<GlobalApprovalStatus, Integer> extractGlobalApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {
    var globalStatusAggregate = aggregations.get(GLOBAL_APPROVAL_STATUS).sterms();
    var result = initializeGlobalApprovalStatusMap();

    for (var bucket : globalStatusAggregate.buckets().array()) {
      var status = GlobalApprovalStatus.parse(bucket.key());
      result.put(status, (int) bucket.docCount());
    }
    return result;
  }

  private static Map<ApprovalStatus, Integer> extractApprovalStatusCounts(
      Map<String, Aggregate> aggregations) {
    var statusAggregate = aggregations.get(APPROVAL_STATUS).sterms();
    var result = initializeApprovalStatusMap();

    for (var bucket : statusAggregate.buckets().array()) {
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
