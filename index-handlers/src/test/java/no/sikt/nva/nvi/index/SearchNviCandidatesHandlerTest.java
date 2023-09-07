package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv(
        "CUSTOM_DOMAIN_BASE_PATH");
    private static final String QUERY_PARAM_QUERY = "query";
    private static final String QUERY_PARAM_OFFSET = "offset";
    private static final String QUERY_PARAM_SIZE = "size";
    private static final String DEFAULT_SEARCH_TERM = "*";
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;
    private static final TypeReference<PaginatedSearchResult<NviCandidateIndexDocument>> TYPE_REF =
        new TypeReference<>() {};
    private static SearchClient<NviCandidateIndexDocument> openSearchClient;
    private static SearchNviCandidatesHandler handler;
    private static ByteArrayOutputStream output;
    private final Context context = mock(Context.class);

    @BeforeEach
    void init() {
        output = new ByteArrayOutputStream();
        openSearchClient = mock(OpenSearchClient.class);
        handler = new SearchNviCandidatesHandler(openSearchClient);
    }

    @Test
    void shouldReturnDocumentFromIndex() throws IOException {
        when(openSearchClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
        handler.handleRequest(request("*"), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
    }

    @Test
    void shouldReturnDocumentFromIndexContainingSingleHitWhenUsingTerms() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(createSearchResponse(document));
        handler.handleRequest(request(document.identifier()), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(1));
    }

    @Test
    void shouldReturnPaginatedSearchResultWithId() throws IOException {
        when(openSearchClient.search(any(), any(), any(), any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE)))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
        handler.handleRequest(request("*"), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var expectedId = constructExpectedUri(DEFAULT_OFFSET_SIZE, DEFAULT_QUERY_SIZE, DEFAULT_SEARCH_TERM);
        assertEquals(expectedId, paginatedSearchResult.getId());
    }

    @Test
    void shouldReturnPaginatedSearchResultWithAggregations() throws IOException {
        var documents = generateNumberOfIndexDocuments(10);
        var aggregationName = randomString();
        var docCount = randomInteger();
        when(openSearchClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(createSearchResponse(documents, 10, aggregationName, docCount));
        handler.handleRequest(request("*"), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedResult =
            objectMapper.readValue(response.getBody(), TYPE_REF);

        assertThat(paginatedResult.getHits(), hasSize(10));
    }

    @Test
    void shouldThrowExceptionWhenSearchFails() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenThrow(RuntimeException.class);
        handler.handleRequest(request(document.identifier()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);

        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
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

    private static URI constructExpectedUri(int offsetSize, int size, String searchTerm) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CUSTOM_DOMAIN_BASE_PATH)
                   .addQueryParameter(QUERY_PARAM_QUERY, searchTerm)
                   .addQueryParameter(QUERY_PARAM_OFFSET, String.valueOf(offsetSize))
                   .addQueryParameter(QUERY_PARAM_SIZE, String.valueOf(size))
                   .getUri();
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
        return new NviCandidateIndexDocument(randomUri(), randomString(),
                                             randomPublicationDetails(), List.of(), 0, randomInteger().toString());
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private List<NviCandidateIndexDocument> generateNumberOfIndexDocuments(int number) {
        return IntStream.range(0, number).boxed().map(i -> singleNviCandidateIndexDocument()).toList();
    }

    private InputStream request(String searchTerm) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .withQueryParameters(Map.of("query", searchTerm,
                                               "offset", String.valueOf(DEFAULT_OFFSET_SIZE),
                                               "size", String.valueOf(DEFAULT_QUERY_SIZE)))
                   .build();
    }
}

