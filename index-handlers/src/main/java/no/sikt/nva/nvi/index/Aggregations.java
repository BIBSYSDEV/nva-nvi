package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.utils.SearchConstants.AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_AFFILIATIONS;
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

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, URI customer) {
        return Map.of(FOR_CONTROL_AGGREGATION_NAME,
                      forControlAggregation(customer.toString()),
                      FOR_CONTROL_MULTIPLE_APPROVALS_AGGREGATION_NAME,
                      forControlMultipleApprovalsAggregation(customer.toString()));
    }

    private static Aggregation forControlMultipleApprovalsAggregation(String customer) {
        var customerQuery = termQuery(customer, jsonPathOf(AFFILIATIONS,ID));
        var approvalStatusQuery = termQuery(PENDING.toString(), jsonPathOf(AFFILIATIONS, APPROVAL_STATUS));
        var assigneeQuery = notExistsQuery(jsonPathOf(AFFILIATIONS, ASSIGNEE));
        var multipleAffiliationsQuery = mustMatch(rangeFromQuery(NUMBER_OF_AFFILIATIONS, 2));

        var nestedQuery = new NestedQuery.Builder()
                              .path(AFFILIATIONS)
                              .query(mustMatch(customerQuery, assigneeQuery, approvalStatusQuery))
                              .build()._toQuery();

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery, multipleAffiliationsQuery))
                   .build();
    }

    private static Aggregation forControlAggregation(String customer) {
        var customerQuery = termQuery(customer, jsonPathOf(AFFILIATIONS,ID));
        var approvalStatusQuery = termQuery(PENDING.toString(), jsonPathOf(AFFILIATIONS, APPROVAL_STATUS));
        var assigneeQuery = notExistsQuery(jsonPathOf(AFFILIATIONS, ASSIGNEE));

        var nestedQuery = new NestedQuery.Builder()
                        .path(AFFILIATIONS)
                        .query(mustMatch(customerQuery, assigneeQuery, approvalStatusQuery))
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
