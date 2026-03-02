package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.StringUtils.isNotBlank;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.query.CandidateQuery;
import no.sikt.nva.nvi.index.query.QueryFilterType;
import nva.commons.core.Environment;
import org.opensearch.client.opensearch._types.mapping.DateProperty;
import org.opensearch.client.opensearch._types.mapping.DoubleNumberProperty;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class SearchConstants {

  public static final String ID = "id";
  public static final String INSTITUTION_ID = "institutionId";
  public static final String ORGANIZATION_ID = "organizationId";
  public static final String ASSIGNEE = "assignee";
  public static final String NUMBER_OF_APPROVALS = "numberOfApprovals";
  public static final String APPROVALS = "approvals";
  public static final String YEAR = "year";
  public static final String TYPE = "type";
  public static final String TITLE = "title";
  public static final String ABSTRACT = "abstract";
  public static final String KEYWORD = "keyword";
  public static final String APPROVAL_STATUS = "approvalStatus";
  public static final String REPORTING_PERIOD = "reportingPeriod";
  public static final String PUBLICATION_DETAILS = "publicationDetails";
  public static final String CONTRIBUTORS = "contributors";
  public static final String NVI_CONTRIBUTORS = "nviContributors";
  public static final String NAME = "name";
  public static final String AFFILIATIONS = "affiliations";
  public static final String PART_OF = "partOf";
  public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
  public static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
  public static final Environment ENVIRONMENT = new Environment();
  public static final String SEARCH_INFRASTRUCTURE_API_HOST = readSearchInfrastructureApiHost();
  public static final String SEARCH_INFRASTRUCTURE_AUTH_URI = readSearchInfrastructureAuthUri();
  public static final String JSON_PATH_DELIMITER = ".";
  public static final String JSON_PATH_CONTRIBUTORS_NAME =
      String.join(
          JSON_PATH_DELIMITER, PUBLICATION_DETAILS,
          CONTRIBUTORS, NAME);
  public static final String JSON_PATH_NVI_CONTRIBUTORS =
      String.join(JSON_PATH_DELIMITER, PUBLICATION_DETAILS, NVI_CONTRIBUTORS);
  public static final TypeMapping MAPPINGS =
      new TypeMapping.Builder().properties(mappingProperties()).build();
  public static final String INVOLVED_ORGS = "involvedOrganizations";
  public static final String GLOBAL_APPROVAL_STATUS = "globalApprovalStatus";
  public static final String POINTS = "points";
  public static final String INSTITUTION_POINTS = "institutionPoints";
  public static final String SECTOR = "sector";
  public static final String ORGANIZATION_SUMMARIES = "organizationSummaries";
  public static final String CREATED_DATE = "createdDate";
  public static final String IDENTIFIER = "identifier";
  public static final String PART_OF_IDENTIFIERS = "partOfIdentifiers";
  public static final String INDEXED_AT = "indexDocumentCreatedAt";

  private SearchConstants() {}

  public static Query constructQuery(CandidateSearchParameters params) {
    var queryFilter =
        isNotBlank(params.filter())
            ? QueryFilterType.parse(params.filter())
            : QueryFilterType.EMPTY_FILTER;
    return new CandidateQuery.Builder()
        .withSearchTerm(params.searchTerm())
        .withAffiliationIdentifiers(
            Optional.ofNullable(params.affiliationIdentifiers()).orElse(List.of()))
        .withExcludeSubUnits(params.excludeSubUnits())
        .withFilter(queryFilter)
        .withUsername(params.username())
        .withTopLevelCristinOrg(params.topLevelOrgUriAsString())
        .withYear(params.year())
        .withCategory(params.category())
        .withTitle(params.title())
        .withAssignee(params.assignee())
        .withExcludeUnassigned(params.excludeUnassigned())
        .withStatuses(params.statuses())
        .withGlobalStatuses(params.globalStatuses())
        .build()
        .toQuery();
  }

  private static Map<String, Property> mappingProperties() {
    return Map.of(
        GLOBAL_APPROVAL_STATUS, keywordProperty(),
        CREATED_DATE, keywordProperty(),
        INDEXED_AT, dateProperty(),
        JSON_PATH_CONTRIBUTORS_NAME, keywordProperty(),
        JSON_PATH_NVI_CONTRIBUTORS, nestedProperty(nviContributorsProperties()),
        APPROVALS, nestedProperty(approvalProperties()));
  }

  private static Property nestedProperty(Map<String, Property> properties) {
    return new NestedProperty.Builder()
        .includeInParent(true)
        .properties(properties)
        .build()
        .toProperty();
  }

  private static String readSearchInfrastructureApiHost() {
    return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_API_HOST");
  }

  private static String readSearchInfrastructureAuthUri() {
    return ENVIRONMENT.readEnv("SEARCH_INFRASTRUCTURE_AUTH_URI");
  }

  private static Map<String, Property> approvalProperties() {
    return Map.of(
        ASSIGNEE, textPropertyWithNestedKeyword(),
        INSTITUTION_ID, keywordProperty(),
        INVOLVED_ORGS, keywordProperty(),
        APPROVAL_STATUS, keywordProperty(),
        ORGANIZATION_SUMMARIES, organizationSummaryProperties(),
        POINTS, pointsProperties(),
        GLOBAL_APPROVAL_STATUS, keywordProperty(),
        SECTOR, keywordProperty());
  }

  private static Property organizationSummaryProperties() {
    return nestedProperty(
        Map.of(
            ORGANIZATION_ID, keywordProperty(),
            APPROVAL_STATUS, keywordProperty(),
            GLOBAL_APPROVAL_STATUS, keywordProperty(),
            POINTS, doubleProperty()));
  }

  private static Property pointsProperties() {
    return nestedProperty(Map.of(INSTITUTION_POINTS, doubleProperty()));
  }

  private static Map<String, Property> nviContributorsProperties() {
    return Map.of(
        ID, keywordProperty(),
        NAME, textPropertyWithNestedKeyword(),
        AFFILIATIONS, nestedProperty(affiliationsProperties()));
  }

  private static Map<String, Property> affiliationsProperties() {
    return Map.of(
        ID, keywordProperty(),
        IDENTIFIER, keywordProperty(),
        PART_OF, keywordProperty(),
        PART_OF_IDENTIFIERS, keywordProperty());
  }

  private static Property keywordProperty() {
    return new KeywordProperty.Builder().build().toProperty();
  }

  private static Property doubleProperty() {
    return new DoubleNumberProperty.Builder().build().toProperty();
  }

  private static Property textPropertyWithNestedKeyword() {
    return new TextProperty.Builder()
        .fields(Map.of(KEYWORD, keywordProperty()))
        .build()
        .toProperty();
  }

  private static Property dateProperty() {
    return new DateProperty.Builder().format("strict_date_optional_time").build().toProperty();
  }
}
