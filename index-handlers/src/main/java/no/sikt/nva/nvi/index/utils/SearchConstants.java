package no.sikt.nva.nvi.index.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.index.aws.CandidateQuery;
import no.sikt.nva.nvi.index.aws.CandidateQuery.QueryFilterType;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.mapping.DoubleNumberProperty;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty.Builder;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class SearchConstants {

    public static final String ID = "id";
    public static final String INSTITUTION_ID = "institutionId";
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
    public static final String JSON_PATH_CONTRIBUTORS = String.join(JSON_PATH_DELIMITER, PUBLICATION_DETAILS,
                                                                    CONTRIBUTORS);
    public static final String INVOLVED_ORGS = "involvedOrganizations";
    public static final String GLOBAL_APPROVAL_STATUS = "globalApprovalStatus";
    public static final String POINTS = "points";
    public static final String INSTITUTION_POINTS = "institutionPoints";
    public static final TypeMapping MAPPINGS = new TypeMapping.Builder().properties(mappingProperties()).build();

    private SearchConstants() {

    }

    public static Query constructQuery(CandidateSearchParameters params) {
        var filterType = QueryFilterType.parse(params.filter())
                             .orElseThrow(() -> new IllegalStateException("unknown filter " + params.filter()));
        return new CandidateQuery.Builder()
                   .withSearchTerm(params.searchTerm())
                   .withInstitutions(Optional.ofNullable(params.affiliations()).orElse(List.of()))
                   .withExcludeSubUnits(params.excludeSubUnits())
                   .withFilter(filterType)
                   .withUsername(params.username())
                   .withTopLevelCristinOrg(params.topLevelOrgUriAsString())
                   .withYear(params.year())
                   .withCategory(params.category())
                   .withTitle(params.title())
                   .withContributor(params.contributor())
                   .withAssignee(params.assignee())
                   .build()
                   .toQuery();
    }

    private static Map<String, Property> mappingProperties() {
        return Map.of(JSON_PATH_CONTRIBUTORS, nestedProperty(contributorsProperties()),
                      APPROVALS, nestedProperty(approvalProperties())
        );
    }

    private static Property nestedProperty(Map<String, Property> properties) {
        return new Builder().includeInParent(true).properties(properties).build()._toProperty();
    }

    private static String readSearchInfrastructureApiHost() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_API_HOST");
    }

    private static String readSearchInfrastructureAuthUri() {
        return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_AUTH_URI");
    }

    private static Map<String, Property> approvalProperties() {
        return Map.of(ASSIGNEE, textPropertyWithNestedKeyword(),
                      INSTITUTION_ID, keywordProperty(),
                      INVOLVED_ORGS, keywordProperty(),
                      APPROVAL_STATUS, keywordProperty(),
                      POINTS, nestedProperty(pointsProperties()),
                      GLOBAL_APPROVAL_STATUS, keywordProperty()
        );
    }

    private static Map<String, Property> pointsProperties() {
        return Map.of(INSTITUTION_POINTS, new DoubleNumberProperty.Builder().build()._toProperty());
    }

    private static Map<String, Property> contributorsProperties() {
        return Map.of(ID, keywordProperty(),
                      NAME, textPropertyWithNestedKeyword(),
                      AFFILIATIONS, nestedProperty(affiliationsProperties()),
                      ROLE, keywordProperty()
        );
    }

    private static Map<String, Property> affiliationsProperties() {
        return Map.of(ID, keywordProperty(), PART_OF, keywordProperty());
    }

    private static Property keywordProperty() {
        return new KeywordProperty.Builder().build()._toProperty();
    }

    private static Property textPropertyWithNestedKeyword() {
        return new TextProperty.Builder().fields(Map.of(KEYWORD, keywordProperty())).build()._toProperty();
    }
}
