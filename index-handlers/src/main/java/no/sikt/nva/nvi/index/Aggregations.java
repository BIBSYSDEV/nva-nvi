package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class Aggregations {

    private static final CharSequence JSON_PATH_DELIMITER = ".";
    public static final String FOR_CONTROL_AGGREGATION_NAME = "for_control";
    public static final String FOR_CONTROL_MULTIPLE_APPROVALS_AGGREGATION_NAME = "for_control_multiple_approvals";
    public static final String FOR_CONTROL_ASSIGNED_AGGREGATION_NAME = "for_control_assigned";
    public static final String FOR_CONTROL_ASSIGNED_MULTIPLE_APPROVALS_AGGREGATION_NAME =
        "for_control_assigned_multiple_approvals";
    public static final int MULTIPLE = 2;

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, URI customer) {
        return Map.of(FOR_CONTROL_AGGREGATION_NAME, forControlAggregation(customer.toString()),
                      FOR_CONTROL_MULTIPLE_APPROVALS_AGGREGATION_NAME,
                      forControlMultipleApprovalsAggregation(customer.toString()),
                      FOR_CONTROL_ASSIGNED_AGGREGATION_NAME, forControlAssignedAggregation(customer.toString()),
                      FOR_CONTROL_ASSIGNED_MULTIPLE_APPROVALS_AGGREGATION_NAME,
                      forControlAssignedMultipleApprovalsAggregation(customer.toString()));
    }

    private static Aggregation forControlAssignedMultipleApprovalsAggregation(String customer) {
        var nestedQuery = new NestedQuery.Builder()
                              .path(APPROVALS)
                              .query(mustMatch(termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                               termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                               existsQuery(jsonPathOf(APPROVALS, ASSIGNEE))))
                              .build()._toQuery();

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, 2))))
                   .build();
    }

    private static Aggregation forControlAssignedAggregation(String customer) {
        var nestedQuery = new NestedQuery.Builder()
                              .path(APPROVALS)
                              .query(mustMatch(termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                               termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                               existsQuery(jsonPathOf(APPROVALS, ASSIGNEE))))
                              .build()._toQuery();

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Aggregation forControlMultipleApprovalsAggregation(String customer) {
        var nestedQuery = new NestedQuery.Builder()
                              .path(APPROVALS)
                              .query(mustMatch(termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                               termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                               notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE))))
                              .build()._toQuery();

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE))))
                   .build();
    }

    private static Aggregation forControlAggregation(String customer) {
        var nestedQuery = new NestedQuery.Builder()
                        .path(APPROVALS)
                        .query(mustMatch(termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                         termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                         notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE))))
                        .build()._toQuery();

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    private static Query notExistsQuery(String field) {
        return new Builder()
                   .mustNot(new ExistsQuery.Builder().field(field).build()._toQuery())
                   .build()._toQuery();
    }

    private static Query existsQuery(String field) {
        return new Builder()
                   .must(notExistsQuery(field))
                   .build()._toQuery();
    }

    private static Query termQuery(String value, String field) {
        return new TermQuery.Builder()
                   .value(new FieldValue.Builder().stringValue(value).build())
                   .field(field)
                   .build()._toQuery();
    }

    private static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
        return new RangeQuery.Builder().field(field).gte(JsonData.of(greaterThanOrEqualTo)).build()._toQuery();
    }

    private static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }
}
