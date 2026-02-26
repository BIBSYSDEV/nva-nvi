package no.sikt.nva.nvi.index.report.query;

import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public sealed interface ReportAggregationQuery<T> permits AllInstitutionsQuery, InstitutionQuery {

  Query query();

  Map<String, Aggregation> aggregations();

  T parseResponse(SearchResponse<Void> response);
}
