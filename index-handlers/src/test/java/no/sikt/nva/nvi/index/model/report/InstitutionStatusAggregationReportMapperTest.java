package no.sikt.nva.nvi.index.model.report;

import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Buckets;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;

class InstitutionStatusAggregationReportMapperTest {

  private static final String TOP_LEVEL_ORG = randomOrganizationId().toString();
  private static final String SUB_ORG_1 = randomOrganizationId().toString();
  private static final String SUB_ORG_2 = randomOrganizationId().toString();
  private static final String YEAR = "2025";

  @Test
  void shouldMapAggregationToReport() {
    var aggregate = createTestAggregate();
    var topLevelOrgUri = URI.create(TOP_LEVEL_ORG);

    var result =
        InstitutionStatusAggregationReportMapper.fromAggregation(aggregate, YEAR, topLevelOrgUri);

    assertNotNull(result);
    assertEquals(YEAR, result.year());
    assertEquals(topLevelOrgUri, result.topLevelOrganizationId());
  }

  @Test
  void shouldExtractTotalsFromAggregation() {
    var aggregate = createTestAggregate();
    var topLevelOrgUri = URI.create(TOP_LEVEL_ORG);

    var result =
        InstitutionStatusAggregationReportMapper.fromAggregation(aggregate, YEAR, topLevelOrgUri);

    var totals = result.totals();
    assertEquals(3, totals.candidateCount());
    assertEquals(BigDecimal.valueOf(15.0), totals.points());
    assertEquals(2, totals.globalApprovalStatus().get(GlobalApprovalStatus.PENDING));
    assertEquals(1, totals.globalApprovalStatus().get(GlobalApprovalStatus.APPROVED));
    assertEquals(2, totals.approvalStatus().get(ApprovalStatus.APPROVED));
    assertEquals(1, totals.approvalStatus().get(ApprovalStatus.PENDING));
  }

  @Test
  void shouldExtractByOrganizationFromAggregation() {
    var aggregate = createTestAggregate();
    var topLevelOrgUri = URI.create(TOP_LEVEL_ORG);

    var result =
        InstitutionStatusAggregationReportMapper.fromAggregation(aggregate, YEAR, topLevelOrgUri);

    var byOrg = result.byOrganization();
    assertEquals(2, byOrg.size());

    var subOrg1 = byOrg.get(URI.create(SUB_ORG_1));
    assertNotNull(subOrg1);
    assertEquals(2, subOrg1.candidateCount());
    assertEquals(BigDecimal.valueOf(10.0), subOrg1.points());

    var subOrg2 = byOrg.get(URI.create(SUB_ORG_2));
    assertNotNull(subOrg2);
    assertEquals(1, subOrg2.candidateCount());
    assertEquals(BigDecimal.valueOf(5.0), subOrg2.points());
  }

  @Test
  void shouldInitializeAllEnumValuesToZeroWhenMissing() {
    var aggregate = createTestAggregate();
    var topLevelOrgUri = URI.create(TOP_LEVEL_ORG);

    var result =
        InstitutionStatusAggregationReportMapper.fromAggregation(aggregate, YEAR, topLevelOrgUri);

    var totals = result.totals();
    assertEquals(0, totals.globalApprovalStatus().get(GlobalApprovalStatus.REJECTED));
    assertEquals(0, totals.globalApprovalStatus().get(GlobalApprovalStatus.DISPUTE));
    assertEquals(0, totals.approvalStatus().get(ApprovalStatus.NEW));
    assertEquals(0, totals.approvalStatus().get(ApprovalStatus.REJECTED));
  }

  private Aggregate createTestAggregate() {
    var totalsAgg = createTotalsAggregate(3, 15.0);
    var subOrgsAgg = createSubOrgsAggregate();

    return new NestedAggregate.Builder()
        .docCount(10)
        .aggregations(Map.of(TOP_LEVEL_ORG, totalsAgg, "subOrgs", subOrgsAgg))
        .build()
        .toAggregate();
  }

  private Aggregate createTotalsAggregate(int docCount, double points) {
    var globalStatusAgg =
        createTermsAggregate(createBucket("Pending", 2), createBucket("Approved", 1));
    var statusAgg = createTermsAggregate(createBucket("Approved", 2), createBucket("Pending", 1));
    var pointsAgg = createPointsAggregate(points, docCount);

    return new FilterAggregate.Builder()
        .docCount(docCount)
        .aggregations(
            Map.of(
                "globalStatus", globalStatusAgg,
                "status", statusAgg,
                "points", pointsAgg))
        .build()
        .toAggregate();
  }

  private Aggregate createSubOrgsAggregate() {
    var org1Bucket = createOrgBucket(SUB_ORG_1, 2, 10.0);
    var org2Bucket = createOrgBucket(SUB_ORG_2, 1, 5.0);

    var byOrgAgg =
        new StringTermsAggregate.Builder()
            .buckets(
                new Buckets.Builder<StringTermsBucket>()
                    .array(List.of(org1Bucket, org2Bucket))
                    .build())
            .sumOtherDocCount(0L)
            .build()
            .toAggregate();

    var orgSummariesNested =
        new NestedAggregate.Builder()
            .docCount(3)
            .aggregations(Map.of("by_organization", byOrgAgg))
            .build()
            .toAggregate();

    return new FilterAggregate.Builder()
        .docCount(3)
        .aggregations(Map.of("org_summaries_nested", orgSummariesNested))
        .build()
        .toAggregate();
  }

  private StringTermsBucket createOrgBucket(String orgId, int docCount, double points) {
    var globalStatusAgg = createTermsAggregate(createBucket("Pending", docCount));
    var statusAgg = createTermsAggregate(createBucket("Approved", docCount));
    var pointsAgg = createPointsAggregate(points, docCount);

    return new StringTermsBucket.Builder()
        .key(orgId)
        .docCount(docCount)
        .aggregations(
            Map.of(
                "globalStatus", globalStatusAgg,
                "status", statusAgg,
                "points", pointsAgg))
        .build();
  }

  private Aggregate createPointsAggregate(double value, int docCount) {
    var sumAgg = new SumAggregate.Builder().value(value).build().toAggregate();
    return new FilterAggregate.Builder()
        .docCount(docCount)
        .aggregations(Map.of("total", sumAgg))
        .build()
        .toAggregate();
  }

  private Aggregate createTermsAggregate(StringTermsBucket... buckets) {
    return new StringTermsAggregate.Builder()
        .buckets(new Buckets.Builder<StringTermsBucket>().array(List.of(buckets)).build())
        .sumOtherDocCount(0L)
        .build()
        .toAggregate();
  }

  private StringTermsBucket createBucket(String key, int docCount) {
    return new StringTermsBucket.Builder().key(key).docCount(docCount).build();
  }
}
