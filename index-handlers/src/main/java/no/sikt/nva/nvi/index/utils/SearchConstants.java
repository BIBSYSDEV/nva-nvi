package no.sikt.nva.nvi.index.utils;

import java.util.Map;
import no.sikt.nva.nvi.index.aws.CandidateQuery;
import no.sikt.nva.nvi.index.aws.CandidateQuery.QueryFilterType;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;

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
    public static final String TYPE = "type";
    public static final String TITLE = "title";
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
    public static final String JSON_PATH_DELIMITER = ".";

    private SearchConstants() {

    }

    public static Query constructQuery(CandidateSearchParameters params) {
        var filterType = QueryFilterType.parse(params.filter())
            .orElseThrow(() -> new IllegalStateException("unknown filter " + params.filter()));
        return new CandidateQuery.Builder()
            .withInstitutions(params.affiliations())
            .withExcludeSubUnits(params.excludeSubUnits())
            .withFilter(filterType)
            .withUsername(params.username())
            .withCustomer(params.customer().toString())
            .withYear(params.year())
            .withCategory(params.category())
            .withTitle(params.title())
            .withContributor(params.contributor())
            .withAssignee(params.assignee())
            .build()
            .toQuery();
    }

    public static TypeMapping mappings() {
        return new TypeMapping.Builder().properties(mappingProperties()).build();
    }

    private static Map<String, Property> mappingProperties() {
        return Map.of(String.join(JSON_PATH_DELIMITER, PUBLICATION_DETAILS, CONTRIBUTORS),
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
        return Map.of(ID, keywordProperty(), ASSIGNEE, textPropertyWithNetsedKeyword(),
                      APPROVAL_STATUS, keywordProperty());
    }

    private static Map<String, Property> contributorsProperties() {
        return Map.of(ID, keywordProperty(),
                      NAME, textPropertyWithNetsedKeyword(),
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

    private static Property textPropertyWithNetsedKeyword() {
        return new Property.Builder().text(new TextProperty.Builder().fields(
            Map.of(KEYWORD, keywordProperty())).build()).build();
    }
}
