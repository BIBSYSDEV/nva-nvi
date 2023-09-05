package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.jetbrains.annotations.NotNull;
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

    private static final String QUERY = "query";
    private static final Environment ENVIRONMENT = new Environment();
    private static final String API_HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv(
        "CUSTOM_DOMAIN_BASE_PATH");
    private static final String QUERY_PARAM_SEARCH_TERM = "query";
    private static final String DEFAULT_SEARCH_TERM = "*";
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
        handler = new SearchNviCandidatesHandler(openSearchClient);
    }

    @Test
    void shouldReturnDocumentFromIndex() throws IOException {
        var expectedDocument = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenReturn(
            createSearchResponse(List.of(expectedDocument), 1));
        handler.handleRequest(request("*", null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var pagesSearchResult = objectMapper.readValue(response.getBody(), TYPE_REF);
        assertEquals(1, pagesSearchResult.getHits().size());
        assertEquals(expectedDocument, pagesSearchResult.getHits().get(0));
    }

    @Test
    void shouldReturnDocumentFromIndexContainingSingleHitWhenUsingTerms() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenReturn(
            createSearchResponse(List.of(document), 1));
        handler.handleRequest(request(document.identifier(), null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var hits = response.getBodyObject(PaginatedSearchResult.class).getHits();
        assertEquals(1, hits.size());
    }

    @Test
    void shouldReturnPaginatedSearchResultWithDefaultOffsetAndSizeIfNotGiven() throws IOException {
        var documents = generateNumberOfIndexDocuments(10);
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenReturn(
            createSearchResponse(documents, documents.size()));
        handler.handleRequest(request(null, null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
        assertEquals(10, paginatedSearchResult.getHits().size());
    }

    @Test
    void shouldReturnPaginatedSearchResultWithId() throws IOException {
        var documents = generateNumberOfIndexDocuments(1);
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenReturn(
            createSearchResponse(documents, documents.size()));
        handler.handleRequest(request(null, null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);

        var expectedId = constructExpectedUri(DEFAULT_OFFSET_SIZE, DEFAULT_QUERY_SIZE, DEFAULT_SEARCH_TERM);
        assertEquals(expectedId, paginatedSearchResult.getId());
    }

    @Test
    void shouldReturnPaginatedSearchResultWithAggregations() throws IOException {
        var documents = generateNumberOfIndexDocuments(10);
        var aggregateName = randomString();
        var docCount = randomInteger();
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenReturn(
            createSearchResponse(documents, documents.size(), aggregateName, docCount));
        handler.handleRequest(request(null, null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, PaginatedSearchResult.class);
        var paginatedSearchResult = response.getBodyObject(PaginatedSearchResult.class);
        assertEquals(docCount, paginatedSearchResult.getAggregations().at("/" + aggregateName + "/docCount").asInt());
    }

    @Test
    void shouldThrowExceptionWhenSearchFails() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenThrow(
            RuntimeException.class);
        handler.handleRequest(request(document.identifier(), null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
    }

    private static URI constructExpectedUri(int offsetSize, int size, String searchTerm) {
        return UriWrapper.fromHost(API_HOST)
                   .addChild(CUSTOM_DOMAIN_BASE_PATH)
                   .addQueryParameter(QUERY_PARAM_SEARCH_TERM, searchTerm)
                   .addQueryParameter(QUERY_PARAM_OFFSET, String.valueOf(offsetSize))
                   .addQueryParameter(QUERY_PARAM_SIZE, String.valueOf(size))
                   .getUri();
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(
        List<NviCandidateIndexDocument> documents, int total) {
        return getNviCandidateIndexDocumentBuilder(documents, total).build();
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(
        List<NviCandidateIndexDocument> documents, int total, String aggregateName, int docCount) {
        return getNviCandidateIndexDocumentBuilder(documents, total)
                   .aggregations(aggregateName, generateFilterAggregate(docCount))
                   .build();
    }

    @NotNull
    private static Aggregate generateFilterAggregate(int docCount) {
        return new Aggregate(new FilterAggregate.Builder().docCount(docCount).build());
    }

    @NotNull
    private static SearchResponse.Builder<NviCandidateIndexDocument> getNviCandidateIndexDocumentBuilder(
        List<NviCandidateIndexDocument> documents, int total) {
        return new Builder<NviCandidateIndexDocument>().hits(constructHitsMetadata(documents))
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(total).build());
    }

    private static HitsMetadata<NviCandidateIndexDocument> constructHitsMetadata(
        List<NviCandidateIndexDocument> documents) {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .total(new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build())
                   .hits(documents.stream().map(SearchNviCandidatesHandlerTest::toHit).toList())
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
                                             randomPublicationDetails(), List.of(), 0);
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    @NotNull
    private static Map<String, String> getQueryParameters(String searchTerm, Integer offset, Integer size) {
        var params = new HashMap<String, String>();
        if (Objects.nonNull(searchTerm)) {
            params.put(QUERY, searchTerm);
        }
        if (Objects.nonNull(offset)) {
            params.put(QUERY_PARAM_OFFSET, String.valueOf(offset));
        }
        if (Objects.nonNull(size)) {
            params.put(QUERY_PARAM_SIZE, String.valueOf(size));
        }
        return params;
    }

    private List<NviCandidateIndexDocument> generateNumberOfIndexDocuments(int number) {
        return IntStream.range(0, number).boxed().map(i -> singleNviCandidateIndexDocument()).toList();
    }

    private InputStream request(String searchTerm, Integer offset, Integer size) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .withQueryParameters(getQueryParameters(searchTerm, offset, size))
                   .build();
    }
}

