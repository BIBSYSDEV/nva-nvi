package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.index.Aggregations.assignmentsQuery;
import static no.sikt.nva.nvi.index.Aggregations.containsPendingStatusQuery;
import static no.sikt.nva.nvi.index.Aggregations.contributorQuery;
import static no.sikt.nva.nvi.index.Aggregations.multipleApprovalsQuery;
import static no.sikt.nva.nvi.index.Aggregations.mustMatch;
import static no.sikt.nva.nvi.index.Aggregations.statusQuery;
import static no.sikt.nva.nvi.index.Aggregations.statusQueryWithAssignee;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;

public final class SearchConstants {

    public static final String PENDING_AGG = "pending";
    public static final String PENDING_COLLABORATION_AGG = "pendingCollaboration";
    public static final String ASSIGNED_AGG = "assigned";
    public static final String ASSIGNED_COLLABORATION_AGG = "assignedCollaboration";
    public static final String APPROVED_AGG = "approved";
    public static final String APPROVED_COLLABORATION_AGG = "approvedCollaboration";
    public static final String REJECTED_AGG = "rejected";
    public static final String REJECTED_COLLABORATION_AGG = "rejectedCollaboration";
    public static final String ASSIGNMENTS_AGG = "assignments";
    public static final String ID = "id";
    public static final String ASSIGNEE = "assignee";
    public static final String NUMBER_OF_APPROVALS = "numberOfApprovals";
    public static final String APPROVALS = "approvals";
    public static final String APPROVAL_STATUS = "approvalStatus";
    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    public static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String SEARCH_INFRASTRUCTURE_API_HOST = readSearchInfrastructureApiHost();
    public static final String SEARCH_INFRASTRUCTURE_AUTH_URI = readSearchInfrastructureAuthUri();

    private SearchConstants() {

    }

    public static Query constructQuery(String institutions, String filter, String username, String customer) {
        var institutionQuery = Objects.nonNull(institutions) ? createInstitutionQuery(institutions) : createMatchAllQuery();
        return isNotEmpty(filter)
                   ? constructQueryWithFilter(filter, username, customer, institutionQuery)
                   : mustMatch(institutionQuery);
    }

    private static Query createMatchAllQuery() {
        return QueryBuilders.matchAll().build()._toQuery();
    }

    public static TypeMapping mappings() {
        return new TypeMapping.Builder().properties(mappingProperties()).build();
    }

    private static boolean isNotEmpty(String filter) {
        return !filter.isEmpty();
    }

    private static Query constructQueryWithFilter(String filter, String username, String customer, Query query) {
        return switch (filter) {
            case PENDING_AGG -> mustMatch(query,
                                          statusQueryWithAssignee(customer, PENDING, false));

            case PENDING_COLLABORATION_AGG -> mustMatch(query,
                                                        statusQueryWithAssignee(customer, PENDING, false),
                                                        multipleApprovalsQuery());

            case ASSIGNED_AGG -> mustMatch(query,
                                           statusQueryWithAssignee(customer, PENDING, true));

            case ASSIGNED_COLLABORATION_AGG -> mustMatch(query,
                                                         statusQueryWithAssignee(customer, PENDING, true),
                                                         multipleApprovalsQuery());

            case APPROVED_AGG -> mustMatch(query,
                                           statusQuery(customer, APPROVED));

            case APPROVED_COLLABORATION_AGG -> mustMatch(query,
                                                         statusQuery(customer, APPROVED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case REJECTED_AGG -> mustMatch(query,
                                           statusQuery(customer, REJECTED));

            case REJECTED_COLLABORATION_AGG -> mustMatch(query,
                                                         statusQuery(customer, REJECTED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case ASSIGNMENTS_AGG -> mustMatch(assignmentsQuery(username, customer));

            default -> throw new IllegalStateException("unknown filter " + filter);
        };
    }

    private static Query createInstitutionQuery(String institutions) {
        return contributorQuery(List.of(institutions.split(",")));
    }

    private static Map<String, Property> mappingProperties() {
        return Map.of(
            "publicationDetails.contributors", new Property.Builder().nested(contributorsNestedProperty()).build(),
            APPROVALS, new Property.Builder().nested(approvalsNestedProperty()).build()
        );
    }

    private static NestedProperty contributorsNestedProperty() {
        return new NestedProperty.Builder().includeInParent(true).properties(contributorsProperties()).build();
    }
    private static NestedProperty approvalsNestedProperty() {
        return new NestedProperty.Builder().includeInParent(true).properties(approvalProperties()).build();
    }

    private static String readSearchInfrastructureApiHost() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_API_HOST");
    }

    private static String readSearchInfrastructureAuthUri() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_AUTH_URI");
    }

    private static Map<String, Property> approvalProperties() {
        return Map.of(ID, keywordProperty(), ASSIGNEE, keywordProperty(), APPROVAL_STATUS, keywordProperty());
    }

    private static Map<String, Property> contributorsProperties() {
        return Map.of(ID, keywordProperty(), "name", keywordProperty(), "affiliations", keywordProperty());
    }

    private static Property keywordProperty() {
        return new Property.Builder().keyword(new KeywordProperty.Builder().build()).build();
    }
}
