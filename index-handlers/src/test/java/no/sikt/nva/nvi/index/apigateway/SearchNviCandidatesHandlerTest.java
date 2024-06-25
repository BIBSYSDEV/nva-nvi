package no.sikt.nva.nvi.index.apigateway;

import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.getGlobalAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.organizationApprovalStatusAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.AggregateResponseTestUtil.randomFilterAggregate;
import static no.sikt.nva.nvi.index.apigateway.utils.MockOpenSearchUtil.createSearchResponse;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_AGGREGATION_TYPE;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_SORT_ORDER;
import static no.sikt.nva.nvi.index.model.search.SearchQueryParameters.QUERY_PARAM_TITLE;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static nva.commons.apigateway.RestRequestHandler.COMMA;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.document.PublicationDate;
import no.sikt.nva.nvi.index.model.document.PublicationDetails;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.OrderByFields;
import no.sikt.nva.nvi.test.FakeViewingScopeValidator;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import nva.commons.logutils.LogUtils;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.problem.Problem;

@Testcontainers
public class SearchNviCandidatesHandlerTest {

    public static final URI TOP_LEVEL_CRISTIN_ORG = URI.create(
        "https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
    public static final String QUERY_PARAM_ORDER_BY = "orderBy";
    private static final String QUERY_PARAM_AFFILIATIONS = "affiliations";
    private static final String QUERY_PARAM_FILTER = "filter";
    private static final String QUERY_PARAM_CATEGORY = "category";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv(
        "CUSTOM_DOMAIN_BASE_PATH");
    private static final String CANDIDATE_PATH = "candidate";
    private static final String QUERY_PARAM_OFFSET = "offset";
    private static final String QUERY_PARAM_SIZE = "size";
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;
    private static final TypeReference<PaginatedSearchResult<NviCandidateIndexDocument>> TYPE_REF =
        new TypeReference<>() {
        };
    private static SearchClient<NviCandidateIndexDocument> openSearchClient;
    private static SearchNviCandidatesHandler handler;
    private static ByteArrayOutputStream output;
    private final Context context = mock(Context.class);

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        openSearchClient = mock(OpenSearchClient.class);
        var viewingScopeValidator = new FakeViewingScopeValidator(true);
        handler = new SearchNviCandidatesHandler(openSearchClient, viewingScopeValidator, ENVIRONMENT);
    }

    @Test
    void shouldReturnBadRequestIfOrderByIsInvalid() throws IOException {
        mockOpenSearchClient();
        var request = createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_ORDER_BY, "invalid"));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(response.getBodyObject(Problem.class).getStatus().getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }

    @Test
    void shouldReturnDocumentFromIndexWhenNoSearchIsSpecified() throws IOException {
        var expectedDocument = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any())).thenReturn(createSearchResponse(expectedDocument));
        handler.handleRequest(emptyRequest(), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
        assertThat(paginatedResult.getHits(), hasSize(1));
        assertEquals(paginatedResult.getHits().get(0), expectedDocument);
    }

    @Test
    void shouldReturnPaginatedSearchResultWithDefaultOffsetAndSizeIfNotGiven() throws IOException {
        mockOpenSearchClient();
        handler.handleRequest(requestWithoutQueryParameters(), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
        var actualId = paginatedSearchResult.getId().toString();
        assertThat(actualId,
                   containsString(QUERY_PARAM_SIZE + "=" + DEFAULT_QUERY_SIZE));
        assertThat(actualId,
                   containsString(QUERY_PARAM_OFFSET + "=" + DEFAULT_OFFSET_SIZE));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithCorrectBaseUriInId() throws IOException {
        mockOpenSearchClient();
        handler.handleRequest(requestWithoutQueryParameters(), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var actualId = paginatedSearchResult.getId().toString();
        var expectedBaseUri = constructBasePath().toString();

        assertThat(actualId, containsString(expectedBaseUri));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithoutQueryParamFilterInIdIfNotGiven() throws IOException {
        mockOpenSearchClient();
        handler.handleRequest(requestWithoutQueryParameters(), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var actualId = paginatedSearchResult.getId().toString();

        assertThat(actualId, Matchers.not(containsString(QUERY_PARAM_FILTER)));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithoutQueryParamCategoryNotGiven() throws IOException {
        mockOpenSearchClient();
        handler.handleRequest(requestWithoutQueryParameters(), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var actualId = paginatedSearchResult.getId().toString();

        assertThat(actualId, Matchers.not(containsString(QUERY_PARAM_CATEGORY)));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithCorrectQueryParamsFilterAndQueryInIdIfGiven() throws IOException {
        mockOpenSearchClient();
        var randomFilter = randomString();
        var randomCategory = randomString();
        var randomTitle = randomString();
        var randomSearchTerm = randomString();
        var randomInstitutions = List.of(randomSiktSubUnit(), randomSiktSubUnit());
        handler.handleRequest(
            requestWithInstitutionsAndFilter(randomSearchTerm, randomInstitutions, randomFilter, randomCategory,
                                             randomTitle),
            output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var actualId = paginatedSearchResult.getId().toString();

        assertThat(actualId, containsString(QUERY_PARAM_FILTER + "=" + randomFilter));
        assertThat(actualId, containsString(QUERY_PARAM_CATEGORY + "=" + randomCategory));
        assertThat(actualId, containsString(QUERY_PARAM_TITLE + "=" + randomTitle));
        assertThat(actualId, containsString(QUERY_PARAM_SEARCH_TERM + "=" + randomSearchTerm));
        var expectedInstitutionQuery = QUERY_PARAM_AFFILIATIONS
                                       + "=" + randomInstitutions.get(0)
                                       + "," + randomInstitutions.get(1);
        var expectedExcludeQuery = QUERY_PARAM_EXCLUDE_SUB_UNITS + "=true";
        assertThat(actualId, containsString(expectedInstitutionQuery));
        assertThat(actualId, containsString(expectedExcludeQuery));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithAggregationTypeInIdIfGiven() throws IOException {
        mockOpenSearchClient();
        var aggregationType = "organizationApprovalStatuses";
        var request = createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_AGGREGATION_TYPE, aggregationType));
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
        var actualId = paginatedSearchResult.getId().toString();
        assertThat(actualId, containsString(QUERY_AGGREGATION_TYPE + "=" + aggregationType));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithOrderByInIdIfGiven() throws IOException {
        mockOpenSearchClient();
        var orderByValue = OrderByFields.CREATED_DATE.getValue();
        var request = createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_ORDER_BY, orderByValue));
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
        var request = createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_PARAM_SORT_ORDER, sortOrderValue));
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
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(documents, aggregationName, randomFilterAggregate(docCount)));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(10));
    }

    @Test
    void shouldUseCamelCaseOnDocCount() throws IOException {
        var documents = generateNumberOfIndexDocuments(1);
        var aggregationName = "someAggregation";
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(documents, aggregationName, randomFilterAggregate(1)));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
        var aggregations = paginatedResult.getAggregations();
        var actualFilterAggregation = aggregations.get(aggregationName);
        var expectedFilterAggregation = """
            {
              "docCount" : 1
            }""";
        assertEquals(expectedFilterAggregation, objectMapper.writeValueAsString(actualFilterAggregation));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithNestedAggregations() throws IOException {
        var documents = generateNumberOfIndexDocuments(3);
        var aggregationName = "someNestedAggregation";
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(documents, aggregationName, organizationApprovalStatusAggregate(
                "someTopLevelOrgId")));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
        var aggregations = paginatedResult.getAggregations();
        var actualNestedAggregation = aggregations.get(aggregationName);
        var expectedNestedAggregation = """
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
        assertEquals(expectedNestedAggregation, objectMapper.writeValueAsString(actualNestedAggregation));
    }

    @Test
    void shouldThrowExceptionWhenSearchFails() throws IOException {
        when(openSearchClient.search(any()))
            .thenThrow(RuntimeException.class);
        handler.handleRequest(emptyRequest(), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
    }

    @Test
    void shouldReturnResultsWithUserTopLevelOrgAsDefaultAffiliationIfNotSet() throws IOException {
        var searchedAffiliations = List.of(randomSiktSubUnit(), randomSiktSubUnit());

        var matcher =
            new CandidateSearchParamsAffiliationMatcher(CandidateSearchParameters.builder()
                                                            .withAffiliations(searchedAffiliations).build());
        when(openSearchClient.search(argThat(matcher)))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));

        var request = requestWithInstitutionsAndTopLevelCristinOrgId(searchedAffiliations, TOP_LEVEL_CRISTIN_ORG);
        handler.handleRequest(request, output, context);

        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
    }

    @Test
    void shouldReturnForbiddenWhenTryingToSearchForAffiliationOutsideOfCustomersCristinIdScope()
        throws IOException {
        var forbiddenAffiliation = "0.0.0.0";
        var validatorReturningFalse = new FakeViewingScopeValidator(false);
        handler = new SearchNviCandidatesHandler(openSearchClient, validatorReturningFalse, ENVIRONMENT);

        var request = requestWithInstitutionsAndTopLevelCristinOrgId(List.of(forbiddenAffiliation),
                                                                     TOP_LEVEL_CRISTIN_ORG);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getDetail()),
                   containsString("User is not allowed to view requested organizations"));
    }

    @Test
    void shouldReturnAllNviCandidatesWhenSearchingWithNviAdminAccessRight() throws IOException {
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
        handler.handleRequest(emptyRequestAsNviAdmin(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
    }

    @Test
    void shouldLogAggregationTypeAndOrganization() throws IOException {
        var aggregationType = "organizationApprovalStatuses";
        var request = createRequest(TOP_LEVEL_CRISTIN_ORG, Map.of(QUERY_AGGREGATION_TYPE, aggregationType));
        final var logAppender = LogUtils.getTestingAppender(SearchNviCandidatesHandler.class);
        handler.handleRequest(request, output, context);
        assertThat(logAppender.getMessages(),
                   containsString("Aggregation type organizationApprovalStatuses requested for "
                                  + "topLevelCristinOrg " + TOP_LEVEL_CRISTIN_ORG));
    }

    @Test
    void shouldUseDefaultSerializationWhenFormatterFormatsUnsupportedAggregationVariant() throws IOException {
        var aggregateName = randomString();
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(List.of(), aggregateName, getGlobalAggregate()));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);
        var aggregations = paginatedResult.getAggregations();
        var actualAggregate = aggregations.get(aggregateName);
        var expectedFilterAggregation = """
            {
              "docCount" : 1
            }""";
        assertEquals(expectedFilterAggregation, objectMapper.writeValueAsString(actualAggregate));
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
        return new PublicationDetails(randomString(), randomString(), randomString(),
                                      PublicationDate.builder().withYear(randomString()).build(), List.of());
    }

    private static InputStream createRequest(URI topLevelCristinOrgId, Map<String, String> queryParams)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(topLevelCristinOrgId)
                   .withAccessRights(topLevelCristinOrgId, AccessRight.MANAGE_NVI_CANDIDATES)
                   .withUserName(randomString())
                   .withQueryParameters(queryParams)
                   .build();
    }

    @NotNull
    private static String toCommaSeperatedStringList(List<String> institutions) {
        return String.join(COMMA, institutions);
    }

    private URI constructBasePath() {
        return UriWrapper.fromHost(API_HOST).addChild(CUSTOM_DOMAIN_BASE_PATH).addChild(CANDIDATE_PATH).getUri();
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
                "20754.6.0.0"
            )
        );
    }

    private InputStream emptyRequest() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .build();
    }

    private InputStream emptyRequestAsNviAdmin() throws JsonProcessingException {
        var topLevelCristinOrgId = randomUri();
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(topLevelCristinOrgId)
                   .withUserName(randomString())
                   .withCurrentCustomer(topLevelCristinOrgId)
                   .withAccessRights(topLevelCristinOrgId, AccessRight.MANAGE_NVI)
                   .build();
    }

    private InputStream requestWithInstitutionsAndFilter(String searchTerm, List<String> affiliationIdentifiers,
                                                         String filter,
                                                         String category,
                                                         String title)
        throws JsonProcessingException {
        return createRequest(randomUri(),
                             Map.of(QUERY_PARAM_AFFILIATIONS, toCommaSeperatedStringList(affiliationIdentifiers),
                                    QUERY_PARAM_EXCLUDE_SUB_UNITS, "true",
                                    QUERY_PARAM_FILTER, filter,
                                    QUERY_PARAM_CATEGORY, category,
                                    QUERY_PARAM_TITLE, title,
                                    QUERY_PARAM_SEARCH_TERM, searchTerm));
    }

    private InputStream requestWithInstitutionsAndTopLevelCristinOrgId(List<String> affiliationIdentifiers,
                                                                       URI cristinId)
        throws JsonProcessingException {
        return createRequest(cristinId,
                             Map.of(QUERY_PARAM_AFFILIATIONS, toCommaSeperatedStringList(affiliationIdentifiers),
                                    QUERY_PARAM_EXCLUDE_SUB_UNITS, "true"));
    }

    private InputStream requestWithoutQueryParameters() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .build();
    }

    public static class CandidateSearchParamsAffiliationMatcher implements ArgumentMatcher<CandidateSearchParameters> {

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