package no.sikt.nva.nvi.index.utils;

import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.Aggregations.PUBLICATION_DATE_AGG;
import static java.util.Objects.nonNull;
import static no.sikt.nva.nvi.index.Aggregations.assignmentsQuery;
import static no.sikt.nva.nvi.index.Aggregations.containsPendingStatusQuery;
import static no.sikt.nva.nvi.index.Aggregations.contributorQuery;
import static no.sikt.nva.nvi.index.Aggregations.jsonPathOf;
import static no.sikt.nva.nvi.index.Aggregations.multipleApprovalsQuery;
import static no.sikt.nva.nvi.index.Aggregations.mustMatch;
import static no.sikt.nva.nvi.index.Aggregations.statusQuery;
import static no.sikt.nva.nvi.index.Aggregations.statusQueryWithAssignee;
import static no.sikt.nva.nvi.index.Aggregations.termQuery;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.APPROVED;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.PENDING;
import static no.sikt.nva.nvi.index.model.ApprovalStatus.REJECTED;
import java.util.List;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;

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
    public static final String PUBLICATION_DATE = "publicationDate";
    public static final String YEAR = "year";
    public static final String MONTH = "month";
    public static final String DAY = "day";
    public static final String KEYWORD = "keyword";
    public static final String APPROVAL_STATUS = "approvalStatus";
    public static final String PUBLICATION_DETAILS = "publicationDetails";
    public static final String CONTRIBUTORS = "contributors";
    public static final String NAME = "name";
    public static final String AFFILIATIONS = "affiliations";
    public static final String PART_OF = "partOf";
    public static final String ROLE = "role";
    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    public static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    public static final Environment ENVIRONMENT = new Environment();
    public static final String SEARCH_INFRASTRUCTURE_API_HOST = readSearchInfrastructureApiHost();
    public static final String SEARCH_INFRASTRUCTURE_AUTH_URI = readSearchInfrastructureAuthUri();

    private SearchConstants() {

    }

    public static Query constructQuery(String affiliations, String filter, String username, String customer,
                                       String year) {
        var affiliationsQuery = Objects.nonNull(affiliations) ? createAffiliationsQuery(affiliations) : null;
        var filterQuery = isNotEmpty(filter) ? constructQueryWithFilter(filter, username, customer) : null;
        var yearQuery = Objects.nonNull(year) ? publicationDateQuery(year) : null;

        var appliedQueries =
            Stream.of(affiliationsQuery, filterQuery, yearQuery)
                .filter(Objects::nonNull).toList().toArray(Query[]::new);

        return appliedQueries.length == 0
                   ? createMatchAllQuery()
                   : mustMatch(appliedQueries);
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

    private static Query constructQueryWithFilter(String filter, String username, String customer) {
        return switch (filter) {
            case PENDING_AGG -> statusQueryWithAssignee(customer, PENDING, false);

            case PENDING_COLLABORATION_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, false),
                                                        multipleApprovalsQuery());

            case ASSIGNED_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, true));

            case ASSIGNED_COLLABORATION_AGG -> mustMatch(statusQueryWithAssignee(customer, PENDING, true),
                                                         multipleApprovalsQuery());

            case APPROVED_AGG -> mustMatch(statusQuery(customer, APPROVED));

            case APPROVED_COLLABORATION_AGG -> mustMatch(statusQuery(customer, APPROVED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case REJECTED_AGG -> mustMatch(statusQuery(customer, REJECTED));

            case REJECTED_COLLABORATION_AGG -> mustMatch(statusQuery(customer, REJECTED),
                                                         containsPendingStatusQuery(),
                                                         multipleApprovalsQuery());

            case ASSIGNMENTS_AGG -> mustMatch(assignmentsQuery(username, customer));

            default -> throw new IllegalStateException("unknown filter " + filter);
        };
    }

    private static Query publicationDateQuery(String year) {
        return termQuery(nonNull(year) ? year : String.valueOf(ZonedDateTime.now().getYear()),
                         jsonPathOf(PUBLICATION_DETAILS, PUBLICATION_DATE, YEAR, KEYWORD));

    }

    private static Query createAffiliationsQuery(String affiliations) {
        return contributorQuery(List.of(affiliations.split(",")));
    }

    private static Map<String, Property> mappingProperties() {
        return Map.of(String.join(".",PUBLICATION_DETAILS, CONTRIBUTORS),
                      new Property.Builder().nested(contributorsNestedProperty()).build(),
                      APPROVALS, new Property.Builder().nested(approvalsNestedProperty()).build()
        );
    }

    private static NestedProperty contributorsNestedProperty() {
        return new NestedProperty.Builder().includeInParent(true).properties(contributorsProperties()).build();
    }

    private static NestedProperty affiliationsNestedProperty() {
        return new NestedProperty.Builder().includeInParent(true).properties(affiliationsProperties()).build();
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
        return Map.of(ID, keywordProperty(),
                      NAME, keywordProperty(),
                      AFFILIATIONS, affiliationsNestedProperty()._toProperty(),
                      ROLE, keywordProperty()
        );
    }

    private static Map<String, Property> affiliationsProperties() {
        return Map.of(ID, keywordProperty(),
                      PART_OF, keywordProperty()
        );
    }

    private static Property keywordProperty() {
        return new Property.Builder().keyword(new KeywordProperty.Builder().build()).build();
    }
}
