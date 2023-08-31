package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import jakarta.json.Json;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.DisMaxQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.opensearch.client.opensearch._types.query_dsl.ScriptQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.w3c.dom.ranges.Range;

public final class Aggregations {

    private static final String AFFILIATIONS = "affiliations";
    private static final String APPROVAL_STATUS = "approvalStatus";
    private static final CharSequence JSON_PATH_DELIMITER = ".";
    public static final String ID = "id";
    public static final String ASSIGNEE = "assignee";
    public static final String DOC_FIELDNAME_VALUES_LENGTH_10 = "params._source.affiliations.length > 1";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, URI customer) {
        return Map.of("to_control", toControlAggregation(customer.toString()),
        "to_control_multiple_affiliations", toControlMultipleAffiliationsAggregation(customer.toString()),
                      "agg", agg(customer.toString()));
    }

    private static String jsonPathOf(String... args) {
        return String.join(JSON_PATH_DELIMITER, args);
    }

    /**
     * @param customer to match affiliation
     * @return Aggregation
     *         where affiliation-id matches customer
     *         and affiliation-approvalStatus is Pending
     *         and affiliation-assignee is not present.
     */

    private static Aggregation toControlAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(queries(queryToMatch(customer, jsonPathOf(AFFILIATIONS, ID)),
                                   queryToMatch(PENDING.getValue(), jsonPathOf(AFFILIATIONS, APPROVAL_STATUS)),
                                   mustNotContainField(ASSIGNEE)))
                   .build();
    }

    /**
     * @param customer to match affiliation
     * @return Aggregation
     *         where affiliation-id matches customer
     *         and affiliation-approvalStatus is Pending
     *         and affiliation-assignee is not present
     *         and there are multiple affiliations.
     */

    private static Aggregation toControlMultipleAffiliationsAggregation(String customer) {
        return new Aggregation.Builder()
                   .filter(queries(queryToMatch(customer, jsonPathOf(AFFILIATIONS, ID)),
                                   queryToMatch(PENDING.getValue(), jsonPathOf(AFFILIATIONS, APPROVAL_STATUS)),
                                   mustNotContainField(ASSIGNEE),
                                   queryToMatchAffiliationsLengthLargerThanOne()))
                   .build();
    }

    private static Aggregation agg(String customer) {
        var termCustomer = new TermQuery.Builder().value(new FieldValue.Builder().stringValue(customer).build())
                                      .field("affiliations.id").build()._toQuery();
        var termStatus = new TermQuery.Builder().value(new FieldValue.Builder().stringValue(PENDING.getValue()).build())
                               .field("affiliations.approvalStatus").build()._toQuery();
        var build = new NestedQuery.Builder()
                        .path("affiliations")
                        .query(new BoolQuery.Builder().must(List.of(termCustomer, termStatus)).build()._toQuery())
                        .build()._toQuery();
        var query = new Query.Builder()
                        .bool(new BoolQuery.Builder().must(List.of(mustNotContainField(ASSIGNEE), build)).build()).build();

        var agg = new Aggregation.Builder()
                   .filter(query)
                   .build();
        return agg;
    }

    private static Query queries(Query... queries) {
        return new Query.Builder()
                   .disMax(new DisMaxQuery.Builder()
                               .queries(Arrays.stream(queries).toList())
                               .build())
                   .build();
    }

    private static Query mustNotContainField(String field) {
        return new Query(new Builder()
                             .mustNot(new Query(new ExistsQuery.Builder().field(field).build())).build());
    }

    private static Query queryToMatch(String value, String field) {
        return new Query(new MatchQuery.Builder()
                             .query(new FieldValue.Builder().stringValue(value).build())
                             .field(field)
                             .build());
    }

    private static Query queryToMatchAffiliationsLengthLargerThanOne() {
        return new Query(new RangeQuery.Builder().field("numberOfApprovals").gte(JsonData.of(1)).build());
    }
}
