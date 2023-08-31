package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.commons.json.JsonUtils;
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
        when(openSearchClient.search(any(), any(), any()))
            .thenReturn(createSearchResponse(singleNviCandidateIndexDocument()));
        handler.handleRequest(request("*"), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, SearchResponseDto.class);
        var hits = response.getBodyObject(SearchResponseDto.class).hits();
        assertThat(hits, hasSize(1));
    }

    @Test
    void shouldReturnDocumentFromIndexContainingSingleHitWhenUsingTerms() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), any(), any())).thenReturn(createSearchResponse(document));
        handler.handleRequest(request(document.identifier()), output, context);
        var response =
            GatewayResponse.fromOutputStream(output, SearchResponseDto.class);
        var hits = response.getBodyObject(SearchResponseDto.class).hits();
        assertThat(hits, hasSize(1));
    }

    @Test
    void shouldThrowExceptionWhenSearchFails() throws IOException {
        var document = singleNviCandidateIndexDocument();
        when(openSearchClient.search(any(), any(), any())).thenThrow(RuntimeException.class);
        handler.handleRequest(request(document.identifier()), output, context);
        var response = GatewayResponse.fromOutputStream(output, Problem.class);
        assertThat(Objects.requireNonNull(response.getBodyObject(Problem.class).getStatus()).getStatusCode(),
                   is(equalTo(HttpURLConnection.HTTP_INTERNAL_ERROR)));
    }

    private static SearchResponse<NviCandidateIndexDocument> createSearchResponse(NviCandidateIndexDocument document) {
        return new SearchResponse.Builder<NviCandidateIndexDocument>()
                   .hits(constructHitsMetadata(document))
                   .took(10)
                   .timedOut(false)
                   .shards(new ShardStatistics.Builder().failed(0).successful(1).total(1).build())
                   .build();
    }

    private static HitsMetadata<NviCandidateIndexDocument> constructHitsMetadata(NviCandidateIndexDocument document) {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .total(new TotalHits.Builder().value(10).relation(TotalHitsRelation.Eq).build())
                   .hits(toHits(document))
                   .total(new TotalHits.Builder().relation(TotalHitsRelation.Eq).value(1).build())
                   .build();
    }

    @NotNull
    private static List<Hit<NviCandidateIndexDocument>> toHits(NviCandidateIndexDocument document) {
        return List.of(new Hit.Builder<NviCandidateIndexDocument>()
                           .id(randomString())
                           .index(NVI_CANDIDATES_INDEX)
                           .source(document)
                           .build());
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomString(),
                                             randomString(),
                                             randomPublicationDetails(), List.of(), 0);
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private InputStream request(String searchTerm) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withQueryParameters(Map.of(QUERY, searchTerm))
                   .withTopLevelCristinOrgId(randomUri())
                   .withUserName(randomString())
                   .build();
    }
}

