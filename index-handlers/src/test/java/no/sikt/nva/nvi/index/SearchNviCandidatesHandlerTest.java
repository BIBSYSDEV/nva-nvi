package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.common.model.PublicationDetails;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

public class SearchNviCandidatesHandlerTest {

    public static final String QUERY = "query";
    private final static Context CONTEXT = mock(Context.class);
    private ByteArrayOutputStream output;
    private SearchClient searchClient;
    private SearchNviCandidatesHandler handler;

    @BeforeEach
    void init() {
        this.output = new ByteArrayOutputStream();
        searchClient = mock(OpenSearchClient.class);
        handler = new SearchNviCandidatesHandler(searchClient);
    }

    @Test
    void shouldReturnDocumentFromIndex() throws IOException {
        var documentFromIndex = singleNviCandidateIndexDocument();
        when(searchClient.search(any())).thenReturn(generateSearchResponse(documentFromIndex));
        handler.handleRequest(request(), output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, SearchResponseDto.class);
        var hits = response.getBodyObject(SearchResponseDto.class).hits();
        assertThat(hits, hasSize(1));
    }

    private static SearchResponse<NviCandidateIndexDocument> generateSearchResponse(
        NviCandidateIndexDocument documentFromIndex) {
        return new SearchResponse.Builder<NviCandidateIndexDocument>()
                   .timedOut(false)
                   .took(15)
                   .shards(singleShard())
                   .hits(singleHit(documentFromIndex))
                   .build();
    }

    private static HitsMetadata<NviCandidateIndexDocument> singleHit(NviCandidateIndexDocument documentFromIndex) {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .hits(constructHit(documentFromIndex))
                   .total(singleTotalHits())
                   .build();
    }

    private static TotalHits singleTotalHits() {
        return new TotalHits.Builder().value(1).relation(TotalHitsRelation.Eq).build();
    }

    private static Hit<NviCandidateIndexDocument> constructHit(NviCandidateIndexDocument documentFromIndex) {
        return new Hit.Builder<NviCandidateIndexDocument>()
                   .index("nvi-candidates")
                   .id(randomString())
                   .source(documentFromIndex)
                   .build();
    }

    private static ShardStatistics singleShard() {
        return new ShardStatistics.Builder().failed(0).successful(1).total(1).build();
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomString(), randomString(),
                                             randomPublicationDetails(), List.of());
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private InputStream request() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(Map.of(QUERY, "*"))
                   .build();
    }
}

