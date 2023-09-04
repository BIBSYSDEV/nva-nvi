package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.NVI_CANDIDATES_INDEX;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zalando.problem.Problem;

@Testcontainers
public class SearchNviCandidatesHandlerTest {

    public static final String QUERY = "query";
    private static final String QUERY_SIZE_PARAM = "size";
    private static final String QUERY_OFFSET_PARAM = "offset";
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
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE))).thenReturn(
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
    void shouldThrowExceptionWhenSearchFails() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), eq(DEFAULT_OFFSET_SIZE), eq(DEFAULT_QUERY_SIZE), any(), any())).thenThrow(
            RuntimeException.class);
        handler.handleRequest(request(document.identifier(), null, null), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(
        List<NviCandidateIndexDocument> documents, int total) {
        return new SearchResponse.Builder<NviCandidateIndexDocument>()
                   .hits(constructHitsMetadata(documents))
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(total).build())
                   .build();
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
            params.put(QUERY_OFFSET_PARAM, String.valueOf(offset));
        }
        if (Objects.nonNull(size)) {
            params.put(QUERY_SIZE_PARAM, String.valueOf(size));
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

