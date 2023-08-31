package no.sikt.nva.nvi.index;

import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.index.model.ApprovalStatus;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;

public final class Aggregations {

    public static final int DEFAULT_AGGREGATION_SIZE = 100;
    private static final String AFFILIATIONS = "affiliations";
    private static final String APPROVAL_STATUS = "approvalStatus";
    private static final String PENDING = "pending";
    private static final String APPROVED = "approved";
    private static final String REJECTED = "rejected";
    public static final String ASSIGNEE = "assignee";
    public static final String KEYWORD = "keyword";
    private static final CharSequence JSON_PATH_DELIMITER = ".";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, URI customer) {
        return Map.of(
            jsonPathOf(APPROVAL_STATUS, PENDING), pendingAggregation(customer.toString()),
            jsonPathOf(APPROVAL_STATUS, APPROVED), approvedAggregation(),
            jsonPathOf(APPROVAL_STATUS, REJECTED), rejectedAggregation(),
            jsonPathOf(ASSIGNEE), termAggregation(jsonPathOf(ASSIGNEE, KEYWORD)),
            jsonPathOf(APPROVAL_STATUS, PENDING,"myinstitution"), termAggregation(jsonPathOf(AFFILIATIONS, "id", "keyword"))
        );
    }

    private static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    private static Aggregation approvedAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(queryNotToMatchApprovalStatusPendingAndRejected())
                   .build();
    }

    private static Query queryNotToMatchApprovalStatusPendingAndRejected(String customer) {
        return new Builder()
                   .mustNot(queryToMatchApprovalStatus(ApprovalStatus.PENDING, customer),
                            queryToMatchApprovalStatus(ApprovalStatus.REJECTED, customer))
                   .build()._toQuery();
    }

    private static Aggregation pendingAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(queryToMatchApprovalStatus(ApprovalStatus.PENDING, customer))
                   .build();
    }

    private static Aggregation termAggregation(String field) {
        return new TermsAggregation.Builder()
                   .field(field)
                   .size(DEFAULT_AGGREGATION_SIZE)
                   .build()._toAggregation();
    }

    private static Aggregation rejectedAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(queryToMatchApprovalStatus(ApprovalStatus.REJECTED, customer))
                   .build();
    }

    private static Query queryToMatchApprovalStatus(ApprovalStatus approvalStatus, String customer) {
        return new Query(new QueryStringQuery.Builder()
                             .query(approvalStatus.toString())
                             .defaultField(jsonPathOf(AFFILIATIONS, APPROVAL_STATUS))
                             .build());
    }
}
