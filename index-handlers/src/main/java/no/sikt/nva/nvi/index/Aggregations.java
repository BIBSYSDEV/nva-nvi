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
import static no.sikt.nva.nvi.index.utils.SearchConstants.GLOBAL_APPROVAL_STATUS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INSTITUTION_ID;
import static no.sikt.nva.nvi.index.utils.SearchConstants.INVOLVED_ORGS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NUMBER_OF_APPROVALS;
import java.util.HashMap;
import java.util.Map;
import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import no.sikt.nva.nvi.index.model.search.SearchAggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.Aggregation.Builder;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class Aggregations {

    public static final String APPROVAL_STATUS_PATH = joinWithDelimiter(APPROVALS, APPROVAL_STATUS);
    public static final String INSTITUTION_ID_PATH = joinWithDelimiter(APPROVALS, INSTITUTION_ID);
    public static final String ASSIGNEE_PATH = joinWithDelimiter(APPROVALS, ASSIGNEE);
    public static final String DISPUTE_AGGREGATION = "dispute";
    private static final int MULTIPLE = 2;
    private static final String ALL_AGGREGATIONS = "all";
    private static final String APPROVAL_ORGANIZATIONS_AGGREGATION = "organizations";
    private static final String STATUS_AGGREGATION = "status";

    private Aggregations() {
    }

    public static Map<String, Aggregation> generateAggregations(String aggregationType, String username,
                                                                String topLevelCristinOrg) {
        return aggregationTypeIsNotSpecified(aggregationType)
                   ? generateAllAggregationTypes(username, topLevelCristinOrg)
                   : generateSingleAggregation(aggregationType, username, topLevelCristinOrg);
    }

    public static Query containsPendingStatusQuery() {
        final var queries = statusQuery(PENDING);
        final var query = mustMatch(queries);
        return nestedQuery(APPROVALS, query);
    }

    public static Query multipleApprovalsQuery() {
        return mustMatch(rangeFromQuery(NUMBER_OF_APPROVALS, MULTIPLE));
    }

    public static Query statusQuery(String topLevelCristinOrg, ApprovalStatus status) {
        final var query = mustMatch(statusForInstitution(topLevelCristinOrg, status));
        return nestedQuery(APPROVALS, query);
    }

    public static Query assignmentsQuery(String username, String topLevelCristinOrg) {
        final var query = mustMatch(assigneeForInstitution(topLevelCristinOrg, username));
        return nestedQuery(APPROVALS, query
        );
    }

    public static Aggregation organizationApprovalStatusAggregations(String topLevelCristinOrg) {

        return new Aggregation.Builder()
                   .filter(mustMatch(approvalInstitutionIdQuery(topLevelCristinOrg)))
                   .aggregations(Map.of(STATUS_AGGREGATION, approvalStatusAggregation(topLevelCristinOrg),
                                        DISPUTE_AGGREGATION, disputeAggregation(topLevelCristinOrg)))
                   .build();
    }

    public static Aggregation totalCountAggregation(String topLevelCristinOrg) {
        final var fieldValueQuery = approvalInstitutionIdQuery(topLevelCristinOrg);
        final var query = mustMatch(fieldValueQuery);
        return filterAggregation(mustMatch(nestedQuery(APPROVALS, query)));
    }

    public static Aggregation statusAggregation(String topLevelCristinOrg, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(topLevelCristinOrg, status)));
    }

    public static Aggregation completedAggregation(String topLevelCristinOrg) {
        final var queries = new Query[]{
            approvalInstitutionIdQuery(topLevelCristinOrg),
            mustNotMatch(PENDING.getValue(), APPROVAL_STATUS_PATH),
            mustNotMatch(NEW.getValue(), APPROVAL_STATUS_PATH)};
        var notPendingQuery = nestedQuery(APPROVALS, mustMatch(queries));
        return filterAggregation(mustMatch(notPendingQuery));
    }

    public static Aggregation assignmentsAggregation(String username, String topLevelCristinOrg) {
        return filterAggregation(mustMatch(assignmentsQuery(username, topLevelCristinOrg)));
    }

    public static Aggregation finalizedCollaborationAggregation(String topLevelCristinOrg, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(topLevelCristinOrg, status), containsPendingStatusQuery(),
                                           multipleApprovalsQuery()));
    }

    public static Aggregation collaborationAggregation(String topLevelCristinOrg, ApprovalStatus status) {
        return filterAggregation(mustMatch(statusQuery(topLevelCristinOrg, status), multipleApprovalsQuery()));
    }

    private static Aggregation approvalStatusAggregation(String topLevelCristinOrg) {
        var statusAggregation = termsAggregation(APPROVALS, APPROVAL_STATUS)._toAggregation();
        var organizationAggregation = new Aggregation.Builder()
                                          .terms(termsAggregation(APPROVALS, INVOLVED_ORGS))
                                          .aggregations(Map.of(STATUS_AGGREGATION, statusAggregation))
                                          .build();
        var filterAggregation = filterAggregation(
            mustMatch(approvalInstitutionIdQuery(topLevelCristinOrg)),
            Map.of(APPROVAL_ORGANIZATIONS_AGGREGATION, organizationAggregation));

        return new Aggregation.Builder()
                   .nested(nestedAggregation(APPROVALS))
                   .aggregations(isNull(topLevelCristinOrg) ? Map.of() : Map.of(topLevelCristinOrg, filterAggregation))
                   .build();
    }

    private static Aggregation disputeAggregation(String topLevelCristinOrg) {
        var approvals = new Aggregation.Builder()
                            .nested(nestedAggregation(APPROVALS))
                            .build();

        var involvedOrgs = new Builder()
                               .terms(termsAggregation(APPROVALS, INVOLVED_ORGS))
                               .aggregations(Map.of("involvedOrgs", approvals))
                               .build();

        var filterTopLevel = new Aggregation.Builder()
                                 .filter(mustMatch(approvalInstitutionIdQuery(topLevelCristinOrg)))
                                 .aggregations("involvedOrgs", involvedOrgs)
                                 .build();

        return filterAggregation(mustMatch(fieldValueQuery(GLOBAL_APPROVAL_STATUS, "Dispute")),
                                 Map.of("filterTopLevel", filterTopLevel));
    }

    private static Query statusQuery(ApprovalStatus approvalStatus) {
        return fieldValueQuery(APPROVAL_STATUS_PATH, approvalStatus.getValue());
    }

    private static Query approvalInstitutionIdQuery(String topLevelCristinOrg) {
        return fieldValueQuery(INSTITUTION_ID_PATH, topLevelCristinOrg);
    }

    private static Query[] assigneeForInstitution(String institutionId, String username) {
        return new Query[]{
            approvalInstitutionIdQuery(institutionId),
            fieldValueQuery(ASSIGNEE_PATH, username)
        };
    }

    private static Query[] statusForInstitution(String institutionId, ApprovalStatus status) {
        return new Query[]{
            approvalInstitutionIdQuery(institutionId),
            statusQuery(status)
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
