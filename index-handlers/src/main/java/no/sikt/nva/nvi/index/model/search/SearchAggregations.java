package no.sikt.nva.nvi.index.model.search;

public enum SearchAggregations {
    NEW_AGG("pending"),
    NEW_COLLABORATION_AGG("pendingCollaboration"),
    PENDING_AGG("assigned"),
    PENDING_COLLABORATION_AGG("assignedCollaboration"),
    APPROVED_AGG("approved"),
    APPROVED_COLLABORATION_AGG("approvedCollaboration"),
    REJECTED_AGG("rejected"),
    REJECTED_COLLABORATION_AGG("rejectedCollaboration"),
    ASSIGNMENTS_AGG("assignments"),
    COMPLETED_AGGREGATION_AGG("completed"),
    TOTAL_COUNT_AGGREGATION_AGG("totalCount"),
    ORGANIZATION_APPROVAL_STATUS_AGGREGATION_AGG("organizationApprovalStatuses"),;

    private final String aggregationName;

    SearchAggregations(String aggregationName) {
        this.aggregationName = aggregationName;
    }

    public String getAggregationName() {
        return aggregationName;
    }
}
