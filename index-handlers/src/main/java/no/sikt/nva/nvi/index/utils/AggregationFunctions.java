package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.common.utils.JsonUtils.jsonPathOf;

import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.SumAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class AggregationFunctions {

  private AggregationFunctions() {}

  public static Aggregation filterAggregation(Query filterQuery) {
    return new Aggregation.Builder().filter(filterQuery).build();
  }

  public static Aggregation filterAggregation(
      Query filterQuery, Map<String, Aggregation> aggregations) {
    return new Aggregation.Builder().filter(filterQuery).aggregations(aggregations).build();
  }

  public static TermsAggregation termsAggregation(String... paths) {
    return new TermsAggregation.Builder().field(jsonPathOf(paths)).build();
  }

  public static NestedAggregation nestedAggregation(String... paths) {
    return new NestedAggregation.Builder().path(jsonPathOf(paths)).build();
  }

  public static Aggregation sumAggregation(String... paths) {
    return new SumAggregation.Builder().field(jsonPathOf(paths)).build().toAggregation();
  }
}
