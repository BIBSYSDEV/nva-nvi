package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery.Builder;
import org.opensearch.client.opensearch._types.query_dsl.DisMaxQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.ScriptQuery;

public final class Aggregations {

    private static final String AFFILIATIONS = "affiliations";
    private static final String APPROVAL_STATUS = "approvalStatus";
    private static final CharSequence JSON_PATH_DELIMITER = ".";
    public static final String ID = "id";
    public static final String ASSIGNEE = "assignee";
    public static final String DOC_FIELDNAME_VALUES_LENGTH_10 = "doc['affiliations'].values.length > 10";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String username, URI customer) {
        return Map.of("to_control", toControlAggregation(customer.toString()),
        "to_control_multiple_affiliations", toControlMultipleAffiliationsAggregation(customer.toString()));
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
        return new Query(new ScriptQuery.Builder()
                             .script(new Script.Builder()
                                         .inline(new InlineScript.Builder().lang(DOC_FIELDNAME_VALUES_LENGTH_10).build()).build())
                             .build());
    }
}
