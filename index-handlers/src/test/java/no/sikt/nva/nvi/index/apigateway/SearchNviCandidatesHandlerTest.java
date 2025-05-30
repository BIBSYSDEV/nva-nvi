package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.common.model.PublicationDateFixtures.getRandomDateInCurrentYearAsDto;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPages;
import static no.sikt.nva.nvi.index.IndexDocumentTestUtils.randomPublicationChannel;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.filterAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.getGlobalAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.organizationApprovalStatusAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
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
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import no.unit.nva.clients.IdentityServiceClient;
import no.unit.nva.clients.UserDto;
import no.unit.nva.clients.UserDto.ViewingScope;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.problem.Problem;

@Testcontainers
class SearchNviCandidatesHandlerTest {

  public static final URI TOP_LEVEL_CRISTIN_ORG =
      URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  public static final String QUERY_PARAM_ORDER_BY = "orderBy";
  public static final String QUERY_ENCODED_COMMA = "%2C";
  private static final String QUERY_PARAM_AFFILIATIONS = "affiliations";
  private static final String QUERY_PARAM_FILTER = "filter";
  private static final String QUERY_PARAM_CATEGORY = "category";
  private static final Environment ENVIRONMENT = new Environment();
  private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
  private static final String CUSTOM_DOMAIN_BASE_PATH =
      ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
  private static final String CANDIDATE_PATH = "candidate";
  private static final String QUERY_PARAM_OFFSET = "offset";
  private static final String QUERY_PARAM_SIZE = "size";
  private static final int DEFAULT_QUERY_SIZE = 10;
  private static final int DEFAULT_OFFSET_SIZE = 0;
  private static final TypeReference<PaginatedSearchResult<NviCandidateIndexDocument>> TYPE_REF =
      new TypeReference<>() {};
  private static SearchClient<NviCandidateIndexDocument> openSearchClient;
  private static SearchNviCandidatesHandler handler;
  private static ByteArrayOutputStream output;
  private final Context context = mock(Context.class);
  private IdentityServiceClient identityServiceClient;

  @BeforeEach
  void init() {
    output = new ByteArrayOutputStream();
    openSearchClient = mock(OpenSearchClient.class);
    var viewingScopeValidator = new FakeViewingScopeValidator(true);
    identityServiceClient = mock(IdentityServiceClient.class);
    handler =
        new SearchNviCandidatesHandler(
            openSearchClient, viewingScopeValidator, identityServiceClient, ENVIRONMENT);
  }

  @Test
  void shouldReturnBadRequestIfOrderByIsInvalid() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    var request =
        createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_ORDER_BY, "invalid"), userName);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    assertThat(
        response.getBodyObject(Problem.class).getStatus().getStatusCode(),
        is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
  }

  @Test
  void shouldReturnDocumentFromIndexWhenNoSearchIsSpecified() throws IOException {
    var expectedDocument = singleNviCandidateIndexDocument();
    when(openSearchClient.search(any())).thenReturn(createSearchResponse(expectedDocument));
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
    assertThat(paginatedResult.getHits(), hasSize(1));
    assertEquals(paginatedResult.getHits().get(0), expectedDocument);
  }

  @Test
  void shouldReturnPaginatedSearchResultWithDefaultOffsetAndSizeIfNotGiven() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
    var actualId = paginatedSearchResult.getId().toString();
    assertThat(actualId, containsString(QUERY_PARAM_SIZE + "=" + DEFAULT_QUERY_SIZE));
    assertThat(actualId, containsString(QUERY_PARAM_OFFSET + "=" + DEFAULT_OFFSET_SIZE));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithCorrectBaseUriInId() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

    var actualId = paginatedSearchResult.getId().toString();
    var expectedBaseUri = constructBasePath().toString();

    assertThat(actualId, containsString(expectedBaseUri));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithoutQueryParamFilterInIdIfNotGiven() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

    var actualId = paginatedSearchResult.getId().toString();

    assertThat(actualId, Matchers.not(containsString(QUERY_PARAM_FILTER)));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithoutQueryParamCategoryNotGiven() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

    var actualId = paginatedSearchResult.getId().toString();

    assertThat(actualId, Matchers.not(containsString(QUERY_PARAM_CATEGORY)));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithCorrectQueryParamsFilterAndQueryInIdIfGiven()
      throws IOException {
    mockOpenSearchClient();
    var randomFilter = randomString();
    var randomCategory = randomString();
    var randomTitle = randomString();
    var randomSearchTerm = randomString();
    var randomInstitutions = List.of(randomSiktSubUnit(), randomSiktSubUnit());
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(
        requestWithInstitutionsAndFilter(
            randomSearchTerm,
            randomInstitutions,
            randomFilter,
            randomCategory,
            randomTitle,
            userName),
        output,
        context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

    var actualId = paginatedSearchResult.getId().toString();

    assertThat(actualId, containsString(QUERY_PARAM_FILTER + "=" + randomFilter));
    assertThat(actualId, containsString(QUERY_PARAM_CATEGORY + "=" + randomCategory));
    assertThat(actualId, containsString(QUERY_PARAM_TITLE + "=" + randomTitle));
    assertThat(actualId, containsString(QUERY_PARAM_SEARCH_TERM + "=" + randomSearchTerm));
    var expectedInstitutionQuery =
        QUERY_PARAM_AFFILIATIONS
            + "="
            + randomInstitutions.get(0)
            + QUERY_ENCODED_COMMA
            + randomInstitutions.get(1);
    var expectedExcludeQuery = QUERY_PARAM_EXCLUDE_SUB_UNITS + "=true";
    assertThat(actualId, containsString(expectedInstitutionQuery));
    assertThat(actualId, containsString(expectedExcludeQuery));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithAggregationTypeInIdIfGiven() throws IOException {
    mockOpenSearchClient();
    var aggregationType = "organizationApprovalStatuses";
    var userName = randomString();
    mockIdentityService(userName);
    var request =
        createRequest(
            TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_AGGREGATION_TYPE, aggregationType), userName);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
    var actualId = paginatedSearchResult.getId().toString();
    assertThat(actualId, containsString(QUERY_AGGREGATION_TYPE + "=" + aggregationType));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithOrderByInIdIfGiven() throws IOException {
    mockOpenSearchClient();
    var userName = randomString();
    mockIdentityService(userName);
    var orderByValue = OrderByFields.CREATED_DATE.getValue();
    var request =
        createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_ORDER_BY, orderByValue), userName);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
    var actualId = paginatedSearchResult.getId().toString();
    assertThat(actualId, containsString(QUERY_PARAM_ORDER_BY + "=" + orderByValue));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithSortOrderInIdIfGiven() throws IOException {
    mockOpenSearchClient();
    var sortOrderValue = "desc";
    var userName = randomString();
    mockIdentityService(userName);
    var request =
        createRequest(
            TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_SORT_ORDER, sortOrderValue), userName);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
    var actualId = paginatedSearchResult.getId().toString();
    assertThat(actualId, containsString(QUERY_PARAM_SORT_ORDER + "=" + sortOrderValue));
  }

  @Test
  void shouldReturnPaginatedSearchResultWithFilterAggregations() throws IOException {
    var documents = generateNumberOfIndexDocuments(10);
    var aggregationName = randomString();
    var docCount = randomInteger();
    var userName = randomString();
    mockIdentityService(userName);
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(documents, aggregationName, filterAggregate(docCount)));
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

    assertThat(paginatedResult.getHits(), hasSize(10));
  }

  @Test
  void shouldUseCamelCaseOnDocCount() throws IOException {
    var documents = generateNumberOfIndexDocuments(1);
    var aggregationName = "someAggregation";
    var userName = randomString();
    mockIdentityService(userName);
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(documents, aggregationName, filterAggregate(1)));
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
    var aggregations = paginatedResult.getAggregations();
    var actualFilterAggregation = aggregations.get(aggregationName);
    var expectedFilterAggregation =
        """
        {
          "docCount" : 1
        }""";
    assertEquals(
        expectedFilterAggregation, objectMapper.writeValueAsString(actualFilterAggregation));
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
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
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
    handler.handleRequest(requestWithoutQueryParameters(randomString()), output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(
        Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
        is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
  }

  @Test
  void shouldReturnResultsWithUsersViewingScopeAsDefaultAffiliationIfNotSet()
      throws IOException, NotFoundException {
    var userName = randomString();
    var usersViewingScope = List.of(randomSiktSubUnit());
    var viewingScopeIncludedUnits = generateRandomUrisWithLastPathElements(usersViewingScope);

    when(identityServiceClient.getUser(eq(userName)))
        .thenReturn(buildGetUserResponse(viewingScopeIncludedUnits));
    mockOpenSearchClientWithParameterMatchingViewingScope(usersViewingScope);
    var noQueryParameters = new HashMap<String, String>();
    var request = createRequest(TOP_LEVEL_CRISTIN_ORG, noQueryParameters, userName);

    handler.handleRequest(request, output, context);

    var response = GatewayResponse.fromOutputStream(output, Problem.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

    assertThat(paginatedResult.getHits(), hasSize(1));
  }

  @Test
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  void shouldReturnForbiddenWhenTryingToSearchForAffiliationOutsideOfCustomersCristinIdScope()
      throws IOException {
    var forbiddenAffiliation = "0.0.0.0"; // This is not an IP address, but a cristin org id example
    var validatorReturningFalse = new FakeViewingScopeValidator(false);
    handler =
        new SearchNviCandidatesHandler(
            openSearchClient, validatorReturningFalse, identityServiceClient, ENVIRONMENT);

    var request =
        requestWithInstitutionsAndTopLevelCristinOrgId(
            List.of(forbiddenAffiliation), TOP_LEVEL_CRISTIN_ORG);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, Problem.class);

    assertThat(
        Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
        is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
    assertThat(
        Objects.requireNonNull(response.getBodyObject(Problem.class).getDetail()),
        containsString("User is not allowed to view requested organizations"));
  }

  @Test
  void shouldReturnAllNviCandidatesWhenSearchingWithNviAdminAccessRight() throws IOException {
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
    handler.handleRequest(emptyRequestAsNviAdmin(), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

    assertThat(paginatedResult.getHits(), hasSize(1));
  }

  @Test
  void shouldUseDefaultSerializationWhenFormatterFormatsUnsupportedAggregationVariant()
      throws IOException {
    var aggregateName = randomString();
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(List.of(), aggregateName, getGlobalAggregate()));
    var userName = randomString();
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
    var aggregations = paginatedResult.getAggregations();
    var actualAggregate = aggregations.get(aggregateName);
    var expectedFilterAggregation =
        """
        {
          "docCount" : 1
        }""";
    assertEquals(expectedFilterAggregation, objectMapper.writeValueAsString(actualAggregate));
  }

  @Test
  void shouldExcludeContributorsInSearchResponse() throws IOException {
    var userName = randomString();
    var expectedExcludeFields = List.of("publicationDetails.contributors");
    mockIdentityService(userName);
    handler.handleRequest(requestWithoutQueryParameters(userName), output, context);
    Mockito.verify(openSearchClient, times(1))
        .search(argThat(argument -> argument.excludeFields().equals(expectedExcludeFields)));
  }

  @Test
  void shouldSearchByReportingPeriodWhenYearParamIsSet() throws IOException {
    var reportedYear = String.valueOf(CURRENT_YEAR - 1);
    var userName = randomString();
    mockIdentityService(userName);
    mockOpenSearchClient();

    var request =
        createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_YEAR, reportedYear), userName);
    handler.handleRequest(request, output, context);
    var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
    var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

    var actualId = paginatedSearchResult.getId().toString();
    assertThat(actualId, containsString(QUERY_PARAM_YEAR + "=" + reportedYear));
  }

  private static void mockOpenSearchClientWithParameterMatchingViewingScope(
      List<String> usersViewingScope) throws IOException {
    var matcher =
        new CandidateSearchParamsAffiliationMatcher(
            CandidateSearchParameters.builder().withAffiliations(usersViewingScope).build());
    when(openSearchClient.search(argThat(matcher)))
        .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
  }

  private static UserDto buildGetUserResponse(List<URI> usersViewingScopeIncludedUnits) {
    return UserDto.builder()
        .withViewingScope(buildViewingScope(usersViewingScopeIncludedUnits))
        .build();
  }

  private static ViewingScope buildViewingScope(List<URI> includedUnits) {
    return ViewingScope.builder().withIncludedUnits(includedUnits).build();
  }

  private static List<URI> generateRandomUrisWithLastPathElements(List<String> lastPathElements) {
    return lastPathElements.stream()
        .map(SearchNviCandidatesHandlerTest::randomUriWithLastPathElement)
        .toList();
  }

  private static URI randomUriWithLastPathElement(String element) {
    return UriWrapper.fromUri(randomUri()).addChild(element).getUri();
  }

  private static void mockOpenSearchClient() throws IOException {
    when(openSearchClient.search(any()))
        .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
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

  private static InputStream createRequest(
      URI topLevelCristinOrgId, Map<String, String> queryParams, String userName)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
        .withTopLevelCristinOrgId(topLevelCristinOrgId)
        .withAccessRights(topLevelCristinOrgId, AccessRight.MANAGE_NVI_CANDIDATES)
        .withUserName(userName)
        .withQueryParameters(queryParams)
        .build();
  }

  @NotNull
  private static String toCommaSeperatedStringList(List<String> institutions) {
    return String.join(COMMA, institutions);
  }

  private OngoingStubbing<UserDto> mockIdentityService(String userName) {
    return attempt(
            () ->
                when(identityServiceClient.getUser(eq(userName)))
                    .thenReturn(buildGetUserResponse(List.of(TOP_LEVEL_CRISTIN_ORG))))
        .orElseThrow();
  }

  private URI constructBasePath() {
    return UriWrapper.fromHost(API_HOST)
        .addChild(CUSTOM_DOMAIN_BASE_PATH)
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

  private InputStream requestWithoutQueryParameters(String userName)
      throws JsonProcessingException {
    return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
        .withTopLevelCristinOrgId(TOP_LEVEL_CRISTIN_ORG)
        .withUserName(userName)
        .build();
  }

  private InputStream emptyRequestAsNviAdmin() throws JsonProcessingException {
    var topLevelCristinOrgId = randomUri();
    return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
        .withTopLevelCristinOrgId(topLevelCristinOrgId)
        .withUserName(randomString())
        .withAccessRights(topLevelCristinOrgId, AccessRight.MANAGE_NVI)
        .build();
  }

  private InputStream requestWithInstitutionsAndFilter(
      String searchTerm,
      List<String> affiliationIdentifiers,
      String filter,
      String category,
      String title,
      String userName)
      throws JsonProcessingException {
    return createRequest(
        TOP_LEVEL_CRISTIN_ORG,
        Map.of(
            QUERY_PARAM_AFFILIATIONS, toCommaSeperatedStringList(affiliationIdentifiers),
            QUERY_PARAM_EXCLUDE_SUB_UNITS, "true",
            QUERY_PARAM_FILTER, filter,
            QUERY_PARAM_CATEGORY, category,
            QUERY_PARAM_TITLE, title,
            QUERY_PARAM_SEARCH_TERM, searchTerm),
        userName);
  }

  private InputStream requestWithInstitutionsAndTopLevelCristinOrgId(
      List<String> affiliationIdentifiers, URI cristinId) throws JsonProcessingException {
    return createRequest(
        cristinId,
        Map.of(
            QUERY_PARAM_AFFILIATIONS,
            toCommaSeperatedStringList(affiliationIdentifiers),
            QUERY_PARAM_EXCLUDE_SUB_UNITS,
            "true"),
        randomString());
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
