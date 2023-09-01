package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class Aggregations {

    private static final CharSequence JSON_PATH_DELIMITER = ".";
    public static final String PENDING_AGGREGATION_NAME = "pending";
    public static final String PENDING_COLLABORATION_AGGREGATION_NAME = "pending_collaboration";
    public static final String ASSIGNED_AGGREGATION_NAME = "assigned";
    public static final String ASSIGNED_COLLABORATION_AGGREGATION_NAME = "assigned_collaboration";
    public static final String APPROVED_AGGREGATION_NAME = "approved";
    public static final String APPROVED_COLLABORATION_AGGREGATION_NAME = "approved_collaboration";
    public static final String REJECTED_AGGREGATION_NAME = "rejected";
    public static final String REJECTED_COLLABORATION_AGGREGATION_NAME = "rejected_collaboration";
    public static final String ASSIGNMENTS_AGGREGATION_NAME = "assignments";
    public static final String COMPLETED_AGGREGATION_NAME = "completed";
    public static final String TOTAL_COUNT_AGGREGATION_NAME = "total_count";
    public static final int MULTIPLE = 2;

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, String customer) {
        return new HashMap<>() {{
            put(PENDING_AGGREGATION_NAME, pendingAggregation(customer));
            put(PENDING_COLLABORATION_AGGREGATION_NAME, pendingCollaborationAggregation(customer));
            put(ASSIGNED_AGGREGATION_NAME, assignedAggregation(customer));
            put(ASSIGNED_COLLABORATION_AGGREGATION_NAME, assignedCollaborationAggregation(customer));
            put(APPROVED_AGGREGATION_NAME, approvedAggregation(customer));
            put(APPROVED_COLLABORATION_AGGREGATION_NAME, approvedCollaborationAggregation(customer));
            put(REJECTED_AGGREGATION_NAME, rejectedAggregation(customer));
            put(REJECTED_COLLABORATION_AGGREGATION_NAME, rejectedCollaborationAggregation(customer));
            put(ASSIGNMENTS_AGGREGATION_NAME, assignmentsAggregation(username, customer));
            put(COMPLETED_AGGREGATION_NAME, completedAggregation(customer));
            put(TOTAL_COUNT_AGGREGATION_NAME, totalCountAggregation(customer));
        }};
    }

    private static Aggregation totalCountAggregation(String customer) {
        var customerQuery = nestedQuery(APPROVALS, termQuery(customer, jsonPathOf(APPROVALS, ID)));

        return new Aggregation.Builder()
                   .filter(mustMatch(customerQuery))
                   .build();
    }

    private static Aggregation completedAggregation(String customer) {
        var notPendingQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      mustNotMatch(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(notPendingQuery))
                   .build();
    }

    private static Aggregation assignmentsAggregation(String username, String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                        termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                        termQuery(username, jsonPathOf(APPROVALS, ASSIGNEE)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Aggregation rejectedCollaborationAggregation(String customer) {
        var rejectedQuery = nestedQuery(APPROVALS,
                                        termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                        termQuery(REJECTED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        var pendingQuery = nestedQuery(APPROVALS,
                                       termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(rejectedQuery,
                                     pendingQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE))))
                   .build();
    }

    private static Aggregation rejectedAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(REJECTED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Aggregation approvedCollaborationAggregation(String customer) {
        var approvedQuery = nestedQuery(APPROVALS,
                                        termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                        termQuery(APPROVED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        var pendingQuery = nestedQuery(APPROVALS,
                                       termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(approvedQuery,
                                     pendingQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE))))
                   .build();
    }

    private static Query nestedQuery(String path, Query... queries) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(mustMatch(queries))
                   .build()._toQuery();
    }

    private static Aggregation approvedAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(APPROVED.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Aggregation assignedCollaborationAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                      existsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, 2))))
                   .build();
    }

    private static Aggregation assignedAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                      existsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery))
                   .build();
    }

    private static Aggregation pendingCollaborationAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                      notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));

        return new Aggregation.Builder()
                   .filter(mustMatch(nestedQuery,
                                     mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE))))
                   .build();
    }

    private static Aggregation pendingAggregation(String customer) {
        var nestedQuery = nestedQuery(APPROVALS,
                                      termQuery(customer, jsonPathOf(APPROVALS, ID)),
                                      termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                      notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));

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

    private static Query mustNotMatch(String value, String field) {
        return new Builder()
                   .mustNot(matchQuery(value, field))
                   .build()._toQuery();
    }

    private static Query matchQuery(String value, String field) {
        return new MatchQuery.Builder().field(field)
                   .query(new FieldValue.Builder().stringValue(value).build())
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
