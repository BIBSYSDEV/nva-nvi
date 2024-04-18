package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.search.SearchAggregation;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

public final class Aggregations {

    public static final int MULTIPLE = 2;
    public static final String ALL_AGGREGATIONS = "all";
    private static final CharSequence JSON_PATH_DELIMITER = ".";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String aggregationType, String username,
                                                                String topLevelCristinOrg) {
        var aggregations = new HashMap<String, Aggregation>();
        if (aggregationTypeIsNotSpecified(aggregationType)) {
            generateAllAggregationTypes(username, topLevelCristinOrg, aggregations);
        } else {
            generateSingleAggregation(aggregationType, username, topLevelCristinOrg, aggregations);
        }
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

    public static Query mustMatch(Query... queries) {
        return new Query.Builder()
                   .bool(new Builder().must(Arrays.stream(queries).toList()).build())
                   .build();
    }

    public static Query termQuery(String value, String field) {
        return nonNull(value) ? toTermQuery(value, field) : matchAllQuery();
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

    public static Aggregation organizationApprovalStatusAggregations(String topLevelCristinOrg) {
        //TODO: Implement NP-46528
        //TODO: TotalCountAggregation is a placeholder for the actual implementation. Remove it when the actual
        // implementation is done.
        return totalCountAggregation(topLevelCristinOrg);
    }

    public static Aggregation statusAggregation(String customer, ApprovalStatus status) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQuery(customer, status)))
                   .build();
    }

    public static Aggregation totalCountAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(
                       mustMatch(nestedQuery(APPROVALS, termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)))))
                   .build();
    }

    public static Aggregation completedAggregation(String customer) {
        var notPendingQuery = nestedQuery(APPROVALS,
                                          termQuery(customer, jsonPathOf(APPROVALS, INSTITUTION_ID)),
                                          mustNotMatch(PENDING.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)),
                                          mustNotMatch(NEW.getValue(), jsonPathOf(APPROVALS, APPROVAL_STATUS)));

        return new Aggregation.Builder()
                   .filter(mustMatch(notPendingQuery))
                   .build();
    }

    public static Aggregation assignmentsAggregation(String username, String customer) {
        return new Aggregation.Builder()
                   .filter(mustMatch(assignmentsQuery(username, customer)))
                   .build();
    }

    public static Aggregation finalizedCollaborationAggregation(String customer, ApprovalStatus status) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQuery(customer, status),
                                     containsPendingStatusQuery(),
                                     multipleApprovalsQuery()))
                   .build();
    }

    public static Aggregation collaborationAggregation(String customer, ApprovalStatus status) {
        return new Aggregation.Builder()
                   .filter(mustMatch(statusQuery(customer, status), multipleApprovalsQuery()))
                   .build();
    }

    private static boolean aggregationTypeIsNotSpecified(String aggregationType) {
        return isNull(aggregationType) || ALL_AGGREGATIONS.equals(aggregationType);
    }

    private static void generateSingleAggregation(String aggregationType, String username, String topLevelCristinOrg,
                                                  HashMap<String, Aggregation> aggregations) {
        var aggregation = SearchAggregation.parse(aggregationType);
        addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
    }

    private static void generateAllAggregationTypes(String username, String topLevelCristinOrg,
                                                    HashMap<String, Aggregation> aggregations) {
        for (var aggregation : SearchAggregation.values()) {
            addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
        }
    }

    private static void addAggregation(String username, String topLevelCristinOrg,
                                       HashMap<String, Aggregation> aggregations,
                                       SearchAggregation aggregation) {
        aggregations.put(aggregation.getAggregationName(),
                         aggregation.generateAggregation(username, topLevelCristinOrg));
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
}
