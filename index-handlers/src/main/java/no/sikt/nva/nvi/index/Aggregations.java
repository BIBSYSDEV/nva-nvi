package no.sikt.nva.nvi.index;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.fieldValueQuery;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.filterAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.joinWithDelimiter;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.mustMatch;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.mustNotMatch;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedAggregation;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.nestedQuery;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.rangeFromQuery;
import static no.sikt.nva.nvi.index.utils.AggregationFunctions.termsAggregation;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.ASSIGNEE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_IDENTIFIER;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INVOLVED_SUB_UNITS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.search.SearchAggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class Aggregations {

    private static final int MULTIPLE = 2;
    private static final String ALL_AGGREGATIONS = "all";
    private static final String APPROVAL_IDENTIFIER_AGGREGATION = "approvalIdentifierAggregation";
    private static final String APPROVAL_INVOLVED_SUB_UNITS_AGGREGATION = "approvalInvolvedSubUnitsAggregation";
    private static final String STATUS_AGGREGATION = "statusAggregation";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String aggregationType, String username,
                                                                String topLevelCristinOrg) {
        return aggregationTypeIsNotSpecified(aggregationType)
                   ? generateAllAggregationTypes(username, topLevelCristinOrg)
                   : generateSingleAggregation(aggregationType, username, topLevelCristinOrg);
    }

    public static Query containsPendingStatusQuery() {
        final var queries = fieldValueQuery(joinWithDelimiter(APPROVALS, APPROVAL_STATUS), PENDING.getValue());
        final var query = mustMatch(queries);
        return nestedQuery(APPROVALS, query);
    }

    public static Query multipleApprovalsQuery() {
        return mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE));
    }

    public static Query statusQuery(String customer, ApprovalStatus status) {
        final var query = mustMatch(statusForInstitution(customer, status));
        return nestedQuery(APPROVALS, query);
    }

    public static Query assignmentsQuery(String username, String customer) {
        final var query = mustMatch(assigneeForInstitution(customer, username));
        return nestedQuery(APPROVALS, query
        );
    }

    public static Aggregation organizationApprovalStatusAggregations(String topLevelCristinOrg) {
        //TODO: Implement NP-46528
        //TODO: TotalCountAggregation is a placeholder for the actual implementation. Remove it when the actual
        // implementation is done.

        var identifiers = termsAggregation(APPROVALS, INSTITUTION_IDENTIFIER)._toAggregation();
        var involvedSubUnits = termsAggregation(APPROVALS, INVOLVED_SUB_UNITS)._toAggregation();
        var statusAggregation = new Aggregation.Builder()
                                    .terms(termsAggregation(APPROVALS, APPROVAL_STATUS))
                                    .aggregations(Map.of(APPROVAL_IDENTIFIER_AGGREGATION, identifiers,
                                                         APPROVAL_INVOLVED_SUB_UNITS_AGGREGATION, involvedSubUnits))
                                    .build();

        return new Aggregation.Builder()
                   .nested(nestedAggregation(APPROVALS))
                   .aggregations(Map.of(STATUS_AGGREGATION, statusAggregation))
                   .build();
    }

    public static Aggregation totalCountAggregation(String customer) {
        final var fieldValueQuery = fieldValueQuery(joinWithDelimiter(APPROVALS, INSTITUTION_ID), customer);
        final var query = mustMatch(fieldValueQuery);
        return filterAggregation(mustMatch(nestedQuery(APPROVALS, query)));
    }

    public static Aggregation statusAggregation(String topLevelCristinOrg, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(topLevelCristinOrg, status)));
    }

    public static Aggregation completedAggregation(String customer) {
        final var queries = new Query[]{
            fieldValueQuery(joinWithDelimiter(APPROVALS, INSTITUTION_ID), customer),
            mustNotMatch(PENDING.getValue(), joinWithDelimiter(APPROVALS, APPROVAL_STATUS)),
            mustNotMatch(NEW.getValue(), joinWithDelimiter(APPROVALS, APPROVAL_STATUS))};
        var notPendingQuery = nestedQuery(APPROVALS, mustMatch(queries));
        return filterAggregation(mustMatch(notPendingQuery));
    }

    public static Aggregation assignmentsAggregation(String username, String customer) {
        return filterAggregation(mustMatch(assignmentsQuery(username, customer)));
    }

    public static Aggregation finalizedCollaborationAggregation(String customer, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(customer, status), containsPendingStatusQuery(),
                                           multipleApprovalsQuery()));
    }

    public static Aggregation collaborationAggregation(String customer, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(customer, status), multipleApprovalsQuery()));
    }

    private static Query[] assigneeForInstitution(String institutionId, String username) {
        return new Query[]{
            fieldValueQuery(joinWithDelimiter(APPROVALS, INSTITUTION_ID), institutionId),
            fieldValueQuery(joinWithDelimiter(APPROVALS, ASSIGNEE), username)
        };
    }

    private static Query[] statusForInstitution(String institutionId, ApprovalStatus status) {
        return new Query[]{
            fieldValueQuery(joinWithDelimiter(APPROVALS, INSTITUTION_ID), institutionId),
            fieldValueQuery(joinWithDelimiter(APPROVALS, APPROVAL_STATUS), status.getValue())
        };
    }

    private static boolean aggregationTypeIsNotSpecified(String aggregationType) {
        return isNull(aggregationType) || ALL_AGGREGATIONS.equals(aggregationType);
    }

    private static Map<String, Aggregation> generateSingleAggregation(String aggregationType, String username,
                                                                      String topLevelCristinOrg) {
        var aggregation = SearchAggregation.parse(aggregationType);
        var aggregations = new HashMap<String, Aggregation>();
        addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
        return aggregations;
    }

    private static Map<String, Aggregation> generateAllAggregationTypes(String username, String topLevelCristinOrg) {
        var aggregations = new HashMap<String, Aggregation>();
        for (var aggregation : SearchAggregation.values()) {
            addAggregation(username, topLevelCristinOrg, aggregations, aggregation);
        }
        return aggregations;
    }

    private static void addAggregation(String username, String topLevelCristinOrg,
                                       Map<String, Aggregation> aggregations,
                                       SearchAggregation aggregation) {
        aggregations.put(aggregation.getAggregationName(),
                         aggregation.generateAggregation(username, topLevelCristinOrg));
    }
}
