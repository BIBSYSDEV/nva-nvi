package no.sikt.nva.nvi.index.apigateway;

import static java.util.Collections.emptyMap;
import static no.sikt.nva.nvi.common.EnvironmentFixtures.getSearchNviCandidatesHandlerEnvironment;
import static no.sikt.nva.nvi.common.model.OrganizationFixtures.randomOrganizationIdentifier;
import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.filterAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.getGlobalAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.organizationApprovalStatusAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_AFFILIATIONS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_CATEGORY;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_FILTER;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_ORDER_BY;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SORT_ORDER;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_TITLE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_YEAR;
import static no.sikt.nva.nvi.test.TestUtils.CURRENT_YEAR;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.RestRequestHandler.COMMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.common.validator.FakeViewingScopeValidator;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.OrderByFields;
import no.sikt.nva.nvi.test.TestUtils;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.zalando.problem.StatusType;

class SearchNviCandidatesHandlerTest extends SearchNviCandidatesHandlerTestBase {

  private static final URI TOP_LEVEL_CRISTIN_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  private static final String QUERY_ENCODED_COMMA = "%2C";
  private static final Environment ENVIRONMENT = getSearchNviCandidatesHandlerEnvironment();
  private static final String CANDIDATE_PATH = "candidate";
  private static SearchClient<NviCandidateIndexDocument> openSearchClient;

  @BeforeEach
  void beforeEach() {
    currentUsername = "CuratorNameHere";
    currentOrganization = TOP_LEVEL_CRISTIN_ORG;
    currentAccessRight = AccessRight.MANAGE_NVI_CANDIDATES;
    mockIdentityService(currentUsername, currentOrganization);

    openSearchClient = mock(OpenSearchClient.class);
    createHandler(openSearchClient);
  }

  @Test
  void shouldReturnBadRequestIfOrderByIsInvalid() {
    mockOpenSearchClient();
    var response = handleBadRequest(Map.of(QUERY_PARAM_ORDER_BY, "invalid"));

    assertThat(response)
        .isNotNull()
        .hasFieldOrPropertyWithValue("status.statusCode", HttpURLConnection.HTTP_BAD_REQUEST);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithCorrectBaseUriInId() {
    mockOpenSearchClient();
    var paginatedResult = handleRequest(emptyMap());
    var actualId = paginatedResult.getId().toString();
    var expectedBaseUri = constructBasePath().toString();
    assertThat(actualId).contains(expectedBaseUri);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithoutQueryParamFilterInIdIfNotGiven() {
    mockOpenSearchClient();
    var paginatedResult = handleRequest(emptyMap());
    var actualId = paginatedResult.getId().toString();

    assertThat(actualId).doesNotContain(QUERY_PARAM_FILTER);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithoutQueryParamCategoryNotGiven() {
    mockOpenSearchClient();
    var paginatedResult = handleRequest(emptyMap());
    var actualId = paginatedResult.getId().toString();

    assertThat(actualId).doesNotContain(QUERY_PARAM_CATEGORY);
  }

  @Test
  void shouldReturnResponseWithAllQueryParamsInId() {
    mockOpenSearchClient();
    var randomFilter = randomString();
    var randomCategory = randomString();
    var randomTitle = randomString();
    var randomSearchTerm = randomString();
    var randomInstitutions = List.of(randomSiktSubUnit(), randomSiktSubUnit());
    var request =
        requestWithInstitutionsAndFilter(
            randomSearchTerm, randomInstitutions, randomFilter, randomCategory, randomTitle);
    var paginatedResult = handleRequest(request);
    var actualId = paginatedResult.getId().toString();

    var expectedInstitutionQuery =
        QUERY_PARAM_AFFILIATIONS
            + "="
            + randomInstitutions.get(0)
            + QUERY_ENCODED_COMMA
            + randomInstitutions.get(1);
    var expectedQueryParameters =
        List.of(
            QUERY_PARAM_FILTER + "=" + randomFilter,
            QUERY_PARAM_CATEGORY + "=" + randomCategory,
            QUERY_PARAM_CATEGORY + "=" + randomCategory,
            QUERY_PARAM_TITLE + "=" + randomTitle,
            QUERY_PARAM_SEARCH_TERM + "=" + randomSearchTerm,
            QUERY_PARAM_EXCLUDE_SUB_UNITS + "=true",
            expectedInstitutionQuery);
    assertThat(actualId).contains(expectedQueryParameters);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithAggregationTypeInIdIfGiven() {
    mockOpenSearchClient();
    var aggregationType = "organizationApprovalStatuses";
    var paginatedResult = handleRequest(Map.of(QUERY_AGGREGATION_TYPE, aggregationType));
    var actualId = paginatedResult.getId().toString();
    assertThat(actualId).contains(QUERY_AGGREGATION_TYPE + "=" + aggregationType);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithOrderByInIdIfGiven() {
    mockOpenSearchClient();
    var orderByValue = OrderByFields.CREATED_DATE.getValue();
    var paginatedResult = handleRequest(Map.of(QUERY_PARAM_ORDER_BY, orderByValue));
    var actualId = paginatedResult.getId().toString();
    assertThat(actualId).contains(QUERY_PARAM_ORDER_BY + "=" + orderByValue);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithSortOrderInIdIfGiven() {
    mockOpenSearchClient();
    var sortOrderValue = "desc";
    var paginatedResult = handleRequest(Map.of(QUERY_PARAM_SORT_ORDER, sortOrderValue));
    var actualId = paginatedResult.getId().toString();
    assertThat(actualId).contains(QUERY_PARAM_SORT_ORDER + "=" + sortOrderValue);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithFilterAggregations() throws IOException {
    var documents = generateNumberOfIndexDocuments(10);
    var aggregationName = randomString();
    var docCount = randomInteger();
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(documents, aggregationName, filterAggregate(docCount)));
    var paginatedResult = handleRequest(emptyMap());

    assertThat(paginatedResult.getHits()).hasSize(10);
  }

  @Test
  void shouldUseCamelCaseOnDocCount() throws IOException {
    var documents = generateNumberOfIndexDocuments(1);
    var aggregationName = "someAggregation";
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(documents, aggregationName, filterAggregate(1)));
    var paginatedResult = handleRequest(emptyMap());
    var aggregations = paginatedResult.getAggregations();

    assertThat(aggregations.get(aggregationName).get("docCount").asInt()).isEqualTo(1);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithNestedAggregations() throws IOException {
    var documents = generateNumberOfIndexDocuments(3);
    var userName = randomString();
    mockIdentityService(userName);
    var aggregationName = "someNestedAggregation";
    when(openSearchClient.search(any()))
        .thenReturn(
            createSearchResponse(
                documents,
                aggregationName,
                organizationApprovalStatusAggregate("someTopLevelOrgId")));
    var paginatedResult = handleRequest(emptyMap());
    var aggregations = paginatedResult.getAggregations();
    var actualNestedAggregation = aggregations.get(aggregationName);
    var expectedNestedAggregation =
        """
        {
          "docCount" : 3,
          "someTopLevelOrgId" : {
            "docCount" : 3,
            "organizations" : {
              "someOrgId" : {
                "docCount" : 3,
                "status" : {
                  "Pending" : {
                    "docCount" : 2
                  }
                }
              }
            }
          }
        }""";
    assertEquals(
        expectedNestedAggregation, objectMapper.writeValueAsString(actualNestedAggregation));
  }

  @Test
  void shouldThrowExceptionWhenSearchFails() throws IOException {
    when(openSearchClient.search(any())).thenThrow(RuntimeException.class);
    var problem = handleBadRequest(emptyMap());

    assertThat(problem)
        .isNotNull()
        .hasFieldOrPropertyWithValue("status.statusCode", HttpURLConnection.HTTP_INTERNAL_ERROR);
  }

  @Test
  void shouldReturnResultsWithUsersViewingScopeAsDefaultAffiliationIfNotSet()
      throws IOException, NotFoundException {
    var usersViewingScope = List.of(randomSiktSubUnit());
    var viewingScopeIncludedUnits = generateRandomUrisWithLastPathElements(usersViewingScope);

    when(identityServiceClient.getUser(currentUsername))
        .thenReturn(buildGetUserResponse(viewingScopeIncludedUnits));
    mockOpenSearchClientWithParameterMatchingViewingScope(usersViewingScope);
    var paginatedResult = handleRequest(emptyMap());

    assertThat(paginatedResult.getHits()).hasSize(1);
  }

  @Test
  void shouldReturnForbiddenForAffiliationOutsideOfCustomersScope() {
    var validatorReturningFalse = new FakeViewingScopeValidator(false);
    createHandler(openSearchClient, validatorReturningFalse);

    var request =
        Map.of(
            QUERY_PARAM_AFFILIATIONS,
            toCommaSeperatedStringList(List.of(randomOrganizationIdentifier())),
            QUERY_PARAM_EXCLUDE_SUB_UNITS,
            "true");
    var response = handleBadRequest(request);

    assertThat(response.getStatus())
        .isNotNull()
        .extracting(StatusType::getStatusCode)
        .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    assertThat(response.getDetail())
        .contains("User is not allowed to view requested organizations");
  }

  @Test
  void shouldUseDefaultSerializationWhenFormatterFormatsUnsupportedAggregationVariant()
      throws IOException {
    var aggregateName = randomString();
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(List.of(), aggregateName, getGlobalAggregate()));
    var userName = randomString();
    mockIdentityService(userName);
    var paginatedResult = handleRequest(emptyMap());
    var aggregations = paginatedResult.getAggregations();

    assertThat(aggregations.get(aggregateName).get("docCount").asInt()).isEqualTo(1);
  }

  @Test
  void shouldExcludeContributorsInSearchResponse() throws IOException {
    var userName = randomString();
    var expectedExcludeFields = List.of("publicationDetails.contributors");
    mockIdentityService(userName);
    handleRequest(emptyMap());
    Mockito.verify(openSearchClient, times(1))
        .search(argThat(argument -> argument.excludeFields().equals(expectedExcludeFields)));
  }

  @Test
  void shouldSearchByReportingPeriodWhenYearParamIsSet() {
    var reportedYear = String.valueOf(CURRENT_YEAR - 1);
    var userName = randomString();
    mockIdentityService(userName);
    mockOpenSearchClient();

    var response = handleRequest(Map.of(QUERY_PARAM_YEAR, reportedYear));

    var actualId = response.getId().toString();
    assertThat(actualId).contains(QUERY_PARAM_YEAR + "=" + reportedYear);
  }

  private static void mockOpenSearchClientWithParameterMatchingViewingScope(
      List<String> usersViewingScope) throws IOException {
    var matcher =
        new CandidateSearchParamsAffiliationMatcher(
            CandidateSearchParameters.builder().withAffiliations(usersViewingScope).build());
    when(openSearchClient.search(argThat(matcher)))
        .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
  }

  private static List<URI> generateRandomUrisWithLastPathElements(List<String> lastPathElements) {
    return lastPathElements.stream()
        .map(SearchNviCandidatesHandlerTest::randomUriWithLastPathElement)
        .toList();
  }

  private static URI randomUriWithLastPathElement(String element) {
    return UriWrapper.fromUri(randomUri()).addChild(element).getUri();
  }

  private static void mockOpenSearchClient() {
    try {
      when(openSearchClient.search(any()))
          .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
    return NviCandidateIndexDocument.builder()
        .withIdentifier(UUID.randomUUID())
        .withPublicationDetails(randomPublicationDetails())
        .withApprovals(List.of())
        .withNumberOfApprovals(0)
        .withPoints(TestUtils.randomBigDecimal())
        .build();
  }

  private static PublicationDetails randomPublicationDetails() {
    return PublicationDetails.builder()
        .withTitle(randomString())
        .withPublicationDate(getRandomDateInCurrentYearAsDto())
        .withPublicationChannel(randomPublicationChannel())
        .withPages(randomPages())
        .build();
  }

  private static String toCommaSeperatedStringList(List<String> institutions) {
    return String.join(COMMA, institutions);
  }

  private void mockIdentityService(String userName) {
    try {
      when(identityServiceClient.getUser(userName))
          .thenReturn(buildGetUserResponse(List.of(TOP_LEVEL_CRISTIN_ORG)));
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private URI constructBasePath() {
    return UriWrapper.fromHost(ENVIRONMENT.readEnv("API_HOST"))
        .addChild(ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH"))
        .addChild(CANDIDATE_PATH)
        .getUri();
  }

  private List<NviCandidateIndexDocument> generateNumberOfIndexDocuments(int number) {
    return IntStream.range(0, number).boxed().map(i -> singleNviCandidateIndexDocument()).toList();
  }

  private String randomSiktSubUnit() {
    return randomElement(
        List.of(
            "20754.1.0.0",
            "20754.2.0.0",
            "20754.3.0.0",
            "20754.4.0.0",
            "20754.5.0.0",
            "20754.6.0.0"));
  }

  private Map<String, String> requestWithInstitutionsAndFilter(
      String searchTerm,
      List<String> affiliationIdentifiers,
      String filter,
      String category,
      String title) {
    return Map.of(
        QUERY_PARAM_AFFILIATIONS, toCommaSeperatedStringList(affiliationIdentifiers),
        QUERY_PARAM_EXCLUDE_SUB_UNITS, "true",
        QUERY_PARAM_FILTER, filter,
        QUERY_PARAM_CATEGORY, category,
        QUERY_PARAM_TITLE, title,
        QUERY_PARAM_SEARCH_TERM, searchTerm);
  }

  public static class CandidateSearchParamsAffiliationMatcher
      implements ArgumentMatcher<CandidateSearchParameters> {

    private final CandidateSearchParameters source;

    public CandidateSearchParamsAffiliationMatcher(CandidateSearchParameters source) {
      this.source = source;
    }

    @Override
    public boolean matches(CandidateSearchParameters other) {
      return other.affiliationIdentifiers().equals(source.affiliationIdentifiers());
    }
  }
}
