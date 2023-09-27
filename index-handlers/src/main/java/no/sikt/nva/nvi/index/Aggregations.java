package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import static no.sikt.nva.nvi.index.utils.SearchConstants.AFFILIATIONS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNMENTS_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.CONTRIBUTORS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PENDING_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PUBLICATION_DETAILS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.REJECTED_COLLABORATION_AGG;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ROLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.PART_OF;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
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
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

public final class Aggregations {

    private static final CharSequence JSON_PATH_DELIMITER = ".";
    public static final String COMPLETED_AGGREGATION_AGG = "completed";
    public static final String TOTAL_COUNT_AGGREGATION_AGG = "totalCount";
    public static final int MULTIPLE = 2;
    public static final String CREATOR_ROLE = "Creator";

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
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));
    }

    public static Query statusQueryWithAssignee(String customer, ApprovalStatus status, boolean hasAssignee) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
                           termQuery(status.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                           hasAssignee
                               ? existsQuery(jsonPathOf(APPROVALS, ASSIGNEE))
                               : notExistsQuery(jsonPathOf(APPROVALS, ASSIGNEE)));
    }

    public static Query contributorQuery(List<String> institutions) {
        return nestedQuery(jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS),
               mustMatch(
                   matchAtLeastOne(
                       termsQuery(institutions, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, ID)),
                       termsQuery(institutions, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, AFFILIATIONS, PART_OF))
                   ),
                   matchQuery(CREATOR_ROLE, jsonPathOf(PUBLICATION_DETAILS, CONTRIBUTORS, ROLE))
               )
        );
    }

    public static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query matchAtLeastOne(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().should(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query notExistsQuery(String field) {
        return new Builder()
                   .mustNot(new ExistsQuery.Builder().field(field).build()._toQuery())
                   .build()._toQuery();
    }

    public static Query termQuery(String value, String field) {
        return new TermQuery.Builder()
                   .value(new FieldValue.Builder().stringValue(value).build())
                   .field(field)
                   .build()._toQuery();
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

    public static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    public static Query assignmentsQuery(String username, String customer) {
        return nestedQuery(APPROVALS,
                           termQuery(customer, jsonPathOf(APPROVALS, ID)),
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
                   .filter(mustMatch(nestedQuery(APPROVALS, termQuery(customer, jsonPathOf(APPROVALS, ID)))))
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
