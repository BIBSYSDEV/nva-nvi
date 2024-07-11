package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import java.util.Arrays;
import java.util.List;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class QueryFunctions {

    private QueryFunctions() {
    }

    public static Query nestedQuery(String path, Query query) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(query)
                   .build()._toQuery();
    }

    public static Query nestedQuery(String path, Query... queries) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(mustMatch(queries))
                   .build()._toQuery();
    }

    public static Query fieldValueQuery(String field, String value) {
        return nonNull(value) ? new TermQuery.Builder()
                                    .field(field)
                                    .value(getFieldValue(value))
                                    .build()._toQuery()
                   : matchAllQuery();
    }

    public static Query termsQuery(List<String> values, String field) {
        var termsFields = values.stream().map(FieldValue::of).toList();
        return new TermsQuery.Builder()
                   .field(field)
                   .terms(new TermsQueryField.Builder().value(termsFields).build())
                   .build()._toQuery();
    }

    public static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
        return new RangeQuery.Builder().field(field).gte(JsonData.of(greaterThanOrEqualTo)).build()._toQuery();
    }

    public static Query mustNotMatch(String value, String field) {
        return mustNotMatch(matchQuery(value, field));
    }

    public static Query mustNotMatch(Query query) {
        return new Builder()
                   .mustNot(query)
                   .build()._toQuery();
    }

    public static Query matchAtLeastOne(Query... queries) {
        return new Query.Builder()
                   .bool(new BoolQuery.Builder().should(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query matchQuery(String value, String field) {
        return new MatchQuery.Builder().field(field)
                   .query(getFieldValue(value))
                   .build()._toQuery();
    }

    private static Query matchAllQuery() {
        return new MatchAllQuery.Builder().build()._toQuery();
    }

    private static FieldValue getFieldValue(String value) {
        return new FieldValue.Builder().stringValue(value).build();
    }
}
