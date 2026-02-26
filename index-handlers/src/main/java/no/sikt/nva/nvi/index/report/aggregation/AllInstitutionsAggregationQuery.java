package no.sikt.nva.nvi.index.report.aggregation;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.sumAggregation;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.POINTS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SECTOR;

import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;

public final class AllInstitutionsAggregationQuery {

  public static final String PER_INSTITUTION = "per_institution";
  public static final String INSTITUTION = "institution";
  public static final String BY_GLOBAL_STATUS = "by_global_status";
  public static final String BY_SECTOR = "by_sector";
  public static final String BY_LOCAL_STATUS = "by_local_status";
  public static final String POINTS_SUM = "points";
  private static final int MAX_INSTITUTIONS = 500;

  private AllInstitutionsAggregationQuery() {}

  public static Aggregation create() {
    var pointsSum = sumAggregation(APPROVALS, POINTS, INSTITUTION_POINTS);

    var byLocalStatus =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, APPROVAL_STATUS), Map.of(POINTS_SUM, pointsSum));

    var byGlobalStatus =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, GLOBAL_APPROVAL_STATUS), Map.of(BY_LOCAL_STATUS, byLocalStatus));

    var bySector = termsAggregationWithSubs(jsonPathOf(APPROVALS, SECTOR), Map.of());

    var institutionAggregation =
        termsAggregationWithSubs(
            jsonPathOf(APPROVALS, INSTITUTION_ID),
            MAX_INSTITUTIONS,
            Map.of(BY_GLOBAL_STATUS, byGlobalStatus, BY_SECTOR, bySector));

    return nestedAggregation(APPROVALS, Map.of(INSTITUTION, institutionAggregation));
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
