package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.SearchNviCandidatesHandler.QUERY_PARAM_EXCLUDE_SUB_UNITS;
import static no.sikt.nva.nvi.index.SearchNviCandidatesHandler.QUERY_PARAM_SEARCH_TERM;
import static no.sikt.nva.nvi.index.SearchNviCandidatesHandler.QUERY_PARAM_TITLE;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDate;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.test.TestUtils;
import no.unit.nva.auth.uriretriever.AuthorizedBackendUriRetriever;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UriWrapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.SearchResponse.Builder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.problem.Problem;

@Testcontainers
public class SearchNviCandidatesHandlerTest {

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
    private static final TypeReference<PaginatedSearchResult<String>> TYPE_REF =
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
        AuthorizedBackendUriRetriever uriRetriever = mock(AuthorizedBackendUriRetriever.class);
        handler = new SearchNviCandidatesHandler(openSearchClient, uriRetriever);

        when(uriRetriever.getRawContent(any(), any())).thenReturn(
            Optional.of(IoUtils.stringFromResources(Path.of("20754.0.0.0.json"))));
    }

    @Test
    void shouldReturnDocumentFromIndexWhenNoSearchIsSpecified() throws IOException {
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
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
    void shouldReturnPaginatedSearchResultWithAggregations() throws IOException {
        var documents = generateNumberOfIndexDocuments(10);
        var aggregationName = randomString();
        var docCount = randomInteger();
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(documents, 10, aggregationName, docCount));
        handler.handleRequest(emptyRequest(), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult = objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(10));
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
        var topLevelCristinOrg = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
        var searchedAffiliations = List.of(randomSiktSubUnit(), randomSiktSubUnit());

        var matcher =
            new CandidateSearchParamsAffiliationMatcher(CandidateSearchParameters.builder()
                                                            .withAffiliations(searchedAffiliations).build());
        when(openSearchClient.search(argThat(matcher)))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));

        var request = requestWithInstitutionsAndTopLevelCristinOrgId(searchedAffiliations, topLevelCristinOrg);
        handler.handleRequest(request, output, context);

        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
    }

    @Test
    void shouldReturnForbiddenWhenTryingToSearchForAffiliationOutsideOfCustomersCristinIdScope() throws IOException {
        when(openSearchClient.search(any()))
            .thenThrow(RuntimeException.class);
        var topLevelCristinOrg = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.0.0.0");
        var searchedAffiliation = URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/0.0.0.0");

        var request = requestWithInstitutionsAndTopLevelCristinOrgId(List.of(searchedAffiliation), topLevelCristinOrg);
        handler.handleRequest(request, output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_UNAUTHORIZED)));
        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getDetail()),
                   containsString(searchedAffiliation.toString()));
    }

    private static void mockOpenSearchClient() throws IOException {
        when(openSearchClient.search(any()))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(NviCandidateIndexDocument document) {
        return new Builder<NviCandidateIndexDocument>().hits(constructHitsMetadata(List.of(document)))
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
                   .build();
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(
        List<NviCandidateIndexDocument> documents, int total, String aggregateName, int docCount) {
        return new Builder<NviCandidateIndexDocument>()
                   .hits(constructHitsMetadata(documents))
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(total).build())
                   .aggregations(aggregateName, new Aggregate(new FilterAggregate.Builder().docCount(docCount).build()))
                   .build();
    }

    private static HitsMetadata<NviCandidateIndexDocument> constructHitsMetadata(
        List<NviCandidateIndexDocument> document) {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .total(new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build())
                   .hits(document.stream().map(SearchNviCandidatesHandlerTest::toHit).collect(Collectors.toList()))
                   .total(new TotalHits.Builder().relation(TotalHitsRelation.Eq).value(1).build())
                   .build();
    }

    private static Hit<NviCandidateIndexDocument> toHit(NviCandidateIndexDocument document) {
        return new Hit.Builder<NviCandidateIndexDocument>()
                   .id(randomString())
                   .index(NVI_CANDIDATES_INDEX)
                   .source(document)
                   .build();
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

    private URI constructBasePath() {
        return UriWrapper.fromHost(API_HOST).addChild(CUSTOM_DOMAIN_BASE_PATH).addChild(CANDIDATE_PATH).getUri();
    }

    private List<NviCandidateIndexDocument> generateNumberOfIndexDocuments(int number) {
        return IntStream.range(0, number).boxed().map(i -> singleNviCandidateIndexDocument()).toList();
    }

    private URI randomSiktSubUnit() {
        return randomElement(
            List.of(
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.1.0.0"),
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.2.0.0"),
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.3.0.0"),
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.4.0.0"),
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.5.0.0"),
                URI.create("https://api.dev.nva.aws.unit.no/cristin/organization/20754.6.0.0")
            )
        );
    }

    private InputStream emptyRequest() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .build();
    }

    private InputStream requestWithInstitutionsAndFilter(String searchTerm, List<URI> institutions, String filter,
                                                         String category,
                                                         String title)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .withQueryParameters(Map.of(QUERY_PARAM_AFFILIATIONS, String.join(COMMA,
                                                                                     institutions.stream()
                                                                                         .map(URI::toString)
                                                                                         .toList()),
                                               QUERY_PARAM_EXCLUDE_SUB_UNITS, "true",
                                               QUERY_PARAM_FILTER, filter,
                                               QUERY_PARAM_CATEGORY, category,
                                               QUERY_PARAM_TITLE, title,
                                               QUERY_PARAM_SEARCH_TERM, searchTerm))
                   .build();
    }

    private InputStream requestWithInstitutionsAndTopLevelCristinOrgId(List<URI> institutions, URI cristinId)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(cristinId)
                   .withUserName(randomString())
                   .withQueryParameters(Map.of(QUERY_PARAM_AFFILIATIONS,
                                               String.join(",",
                                                           institutions.stream().map(URI::toString).toList()),
                                               QUERY_PARAM_EXCLUDE_SUB_UNITS,
                                               "true"))
                   .build();
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
            return other.affiliations().equals(source.affiliations());
        }
    }
}