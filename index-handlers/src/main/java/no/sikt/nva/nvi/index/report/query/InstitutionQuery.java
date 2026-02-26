package no.sikt.nva.nvi.index.report.query;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;
import static no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery.PER_INSTITUTION;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.QueryFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.common.service.model.NviPeriod;
import no.sikt.nva.nvi.index.report.aggregation.AllInstitutionsAggregationQuery;
import no.sikt.nva.nvi.index.report.model.InstitutionAggregationResult;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.SearchResponse;

public record InstitutionQuery(NviPeriod period, URI institutionId)
    implements ReportAggregationQuery<Optional<InstitutionAggregationResult>> {

  private static final String REPORTING_PERIOD_YEAR = "reportingPeriod.year";
  private static final String REPORTED = "reported";

  @Override
  public Query query() {
    var yearFilter = yearFilter();
    var institutionFilter = institutionFilter();
    if (period.isClosed()) {
      return new BoolQuery.Builder()
          .filter(yearFilter, reportedFilter(), institutionFilter)
          .build()
          .toQuery();
    }
    return new BoolQuery.Builder().filter(yearFilter, institutionFilter).build().toQuery();
  }

  @Override
  public Map<String, Aggregation> aggregations() {
    return Map.of(PER_INSTITUTION, AllInstitutionsAggregationQuery.create());
  }

  @Override
  public Optional<InstitutionAggregationResult> parseResponse(SearchResponse<Void> response) {
    return new AllInstitutionsQuery(period)
        .parseResponse(response).stream()
            .filter(result -> result.institutionId().equals(institutionId))
            .findFirst();
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

  private Query institutionFilter() {
    var institutionTermQuery =
        fieldValueQuery(jsonPathOf(APPROVALS, INSTITUTION_ID), institutionId.toString());
    return nestedQuery(APPROVALS, institutionTermQuery);
  }
}
