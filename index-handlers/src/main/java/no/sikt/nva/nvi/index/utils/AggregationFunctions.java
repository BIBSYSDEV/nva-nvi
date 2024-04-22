package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import java.util.Arrays;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class AggregationFunctions {

    private static final CharSequence JSON_PATH_DELIMITER = ".";

    private AggregationFunctions() {
    }

    public static Query nestedQuery(String path, Query query) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(query)
                   .build()._toQuery();
    }

    public static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query fieldValueQuery(String field, String value) {
        return nonNull(value) ? new TermQuery.Builder()
                                    .field(field)
                                    .value(getFieldValue(value))
                                    .build()._toQuery()
                   : matchAllQuery();
    }

    public static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
        return new RangeQuery.Builder().field(field).gte(JsonData.of(greaterThanOrEqualTo)).build()._toQuery();
    }

    public static String joinWithDelimiter(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    public static Aggregation filterAggregation(Query filterQuery) {
        return new Aggregation.Builder()
                   .filter(filterQuery)
                   .build();
    }

    public static TermsAggregation termsAggregation(String... paths) {
        return new TermsAggregation.Builder()
                   .field(joinWithDelimiter(paths))
                   .build();
    }

    public static Query mustNotMatch(String value, String field) {
        return new Builder()
                   .mustNot(matchQuery(value, field))
                   .build()._toQuery();
    }

    public static Query matchQuery(String value, String field) {
        return new MatchQuery.Builder().field(field)
                   .query(getFieldValue(value))
                   .build()._toQuery();
    }

    public static NestedAggregation nestedAggregation(String... paths) {
        return new NestedAggregation.Builder()
                   .path(joinWithDelimiter(paths))
                   .build();
    }

    private static Query matchAllQuery() {
        return new MatchAllQuery.Builder().build()._toQuery();
    }

    private static FieldValue getFieldValue(String value) {
        return new FieldValue.Builder().stringValue(value).build();
    }
}
