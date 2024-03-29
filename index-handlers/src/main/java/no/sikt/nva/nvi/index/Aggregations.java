package no.sikt.nva.nvi.index;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNMENTS_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_COLLABORATION_AGG;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class Aggregations {

    public static final String COMPLETED_AGGREGATION_AGG = "completed";
    public static final String TOTAL_COUNT_AGGREGATION_AGG = "totalCount";
    public static final int MULTIPLE = 2;
    private static final CharSequence JSON_PATH_DELIMITER = ".";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, String customer) {
        var aggregations = new HashMap<String, Aggregation>();
        aggregations.put(PENDING_AGG, statusAggregation(customer, PENDING, false));
        aggregations.put(PENDING_COLLABORATION_AGG, collaborationAggregation(customer, PENDING, false));
        aggregations.put(ASSIGNED_AGG, statusAggregation(customer, PENDING, true));
        aggregations.put(ASSIGNED_COLLABORATION_AGG, collaborationAggregation(customer, PENDING, true));
        aggregations.put(APPROVED_AGG, statusAggregation(customer, APPROVED));
        aggregations.put(APPROVED_COLLABORATION_AGG, collaborationAggregation(customer, APPROVED));
        aggregations.put(REJECTED_AGG, statusAggregation(customer, REJECTED));
        aggregations.put(REJECTED_COLLABORATION_AGG, collaborationAggregation(customer, REJECTED));
        aggregations.put(ASSIGNMENTS_AGG, assignmentsAggregation(username, customer));
        aggregations.put(COMPLETED_AGGREGATION_AGG, completedAggregation(customer));
        aggregations.put(TOTAL_COUNT_AGGREGATION_AGG, totalCountAggregation(customer));
        return aggregations;
    }

    public static Query containsPendingStatusQuery() {
        return nestedQuery(APPROVALS, termQuery(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    public static Query nestedQuery(String path, Query... queries) {
        return new NestedQuery.Builder()
                   .path(path)
                   .query(mustMatch(queries))
                   .build()._toQuery();
    }

    public static Query multipleApprovalsQuery() {
        return mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE));
    }

    public static Query statusQuery(String customer, ApprovalStatus status) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    public static Query statusQueryWithAssignee(String customer, ApprovalStatus status, boolean hasAssignee) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                           hasAssignee
                               ? existsQuery(jsonPathOf(APPROVALS, ASSIGNEE))
                               : notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));
    }

    public static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query notExistsQuery(String field) {
        return new Builder()
                   .mustNot(new ExistsQuery.Builder().field(field).build()._toQuery())
                   .build()._toQuery();
    }

    public static Query termQuery(String value, String field) {
        return nonNull(value) ? toTermQuery(value, field) : matchAllQuery();
    }

    private static Query matchAllQuery() {
        return new MatchAllQuery.Builder().build()._toQuery();
    }

    private static Query toTermQuery(String value, String field) {
        return new TermQuery.Builder()
                   .value(new FieldValue.Builder().stringValue(value).build())
                   .field(field)
                   .build()._toQuery();
    }

    public static Query rangeFromQuery(String field, int greaterThanOrEqualTo) {
        return new RangeQuery.Builder().field(field).gte(JsonData.of(greaterThanOrEqualTo)).build()._toQuery();
    }

    public static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    public static Query assignmentsQuery(String username, String customer) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                           termQuery(username, jsonPathOf(APPROVALS, ASSIGNEE)));
    }

    private static Aggregation statusAggregation(String customer, ApprovalStatus status) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQuery(customer, status)))
                   .build();
    }

    private static Aggregation statusAggregation(String customer, ApprovalStatus status, boolean assigneeExists) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQueryWithAssignee(customer, status, assigneeExists)))
                   .build();
    }

    private static Aggregation totalCountAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(
                       mustMatch(nestedQuery(APPROVALS, termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)))))
                   .build();
    }

    private static Aggregation completedAggregation(String customer) {
        var notPendingQuery = nestedQuery(APPROVALS,
                                          termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                                          mustNotMatch(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(notPendingQuery))
                   .build();
    }

    private static Aggregation assignmentsAggregation(String username, String customer) {
        return new Aggregation.Builder()
                   .filter(mustMatch(assignmentsQuery(username, customer)))
                   .build();
    }

    private static Aggregation collaborationAggregation(String customer, ApprovalStatus status) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQuery(customer, status),
                                     containsPendingStatusQuery(),
                                     multipleApprovalsQuery()))
                   .build();
    }

    private static Aggregation collaborationAggregation(String customer,
                                                        ApprovalStatus status,
                                                        boolean assigneeExists) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQueryWithAssignee(customer, status, assigneeExists),
                                     multipleApprovalsQuery()))
                   .build();
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
                   .must(new ExistsQuery.Builder().field(field).build()._toQuery())
                   .build()._toQuery();
    }
}
