package no.sikt.nva.nvi.index.model.search;

import static no.sikt.nva.nvi.index.Aggregations.collaborationAggregation;
import static no.sikt.nva.nvi.index.Aggregations.completedAggregation;
import static no.sikt.nva.nvi.index.Aggregations.disputeAggregation;
import static no.sikt.nva.nvi.index.Aggregations.finalizedCollaborationAggregation;
import static no.sikt.nva.nvi.index.Aggregations.organizationApprovalStatusAggregations;
import static no.sikt.nva.nvi.index.Aggregations.statusAggregation;
import static no.sikt.nva.nvi.index.Aggregations.totalCountAggregation;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.NEW;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.document.ApprovalStatus.REJECTED;
import java.util.Arrays;
import java.util.function.BiFunction;
import no.sikt.nva.nvi.index.Aggregations;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

public enum SearchAggregation {
    NEW_AGG("pending", (username, topLevelCristinOrg) -> statusAggregation(topLevelCristinOrg, NEW)),
    NEW_COLLABORATION_AGG("pendingCollaboration",
                          (username, topLevelCristinOrg) -> collaborationAggregation(topLevelCristinOrg, NEW)),
    PENDING_AGG("assigned", (username, topLevelCristinOrg) -> statusAggregation(topLevelCristinOrg, PENDING)),
    PENDING_COLLABORATION_AGG("assignedCollaboration",
                              (username, topLevelCristinOrg) -> collaborationAggregation(topLevelCristinOrg, PENDING)),
    APPROVED_AGG("approved", (username, topLevelCristinOrg) -> statusAggregation(topLevelCristinOrg, APPROVED)),
    APPROVED_COLLABORATION_AGG("approvedCollaboration",
                               (username, topLevelCristinOrg) -> finalizedCollaborationAggregation(topLevelCristinOrg,
                                                                                                   APPROVED)),
    REJECTED_AGG("rejected", (username, topLevelCristinOrg) -> statusAggregation(topLevelCristinOrg, REJECTED)),
    REJECTED_COLLABORATION_AGG("rejectedCollaboration",
                               (username, topLevelCristinOrg) -> finalizedCollaborationAggregation(
                                   topLevelCristinOrg, REJECTED)),
    DISPUTED_AGG("dispute", (username, topLevelCristinOrg) -> disputeAggregation(topLevelCristinOrg)),
    ASSIGNMENTS_AGG("assignments", Aggregations::assignmentsAggregation),
    COMPLETED_AGGREGATION_AGG("completed",
                              (username, topLevelCristinOrg) -> completedAggregation(topLevelCristinOrg)),
    TOTAL_COUNT_AGGREGATION_AGG("totalCount",
                                (username, topLevelCristinOrg) -> totalCountAggregation(topLevelCristinOrg)),
    ORGANIZATION_APPROVAL_STATUS_AGGREGATION("organizationApprovalStatuses",
                                             (username, topLevelCristinOrg) -> organizationApprovalStatusAggregations(
                                                 topLevelCristinOrg));

    private final String aggregationName;

    private final BiFunction<String, String, Aggregation> aggregationFunction;

    SearchAggregation(String aggregationName, BiFunction<String, String, Aggregation> aggregationFunction) {
        this.aggregationName = aggregationName;
        this.aggregationFunction = aggregationFunction;
    }

    public static SearchAggregation parse(String candidate) {
        return Arrays.stream(SearchAggregation.values())
                   .filter(type -> type.getAggregationName().equalsIgnoreCase(candidate))
                   .findFirst()
                   .orElse(null);
    }

    public Aggregation generateAggregation(String username, String topLevelCristinOrg) {
        return aggregationFunction.apply(username, topLevelCristinOrg);
    }

    public String getAggregationName() {
        return aggregationName;
    }
}
