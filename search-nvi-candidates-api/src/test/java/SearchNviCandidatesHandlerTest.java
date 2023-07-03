import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
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
import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.common.model.PublicationDetails;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.nvi.search.Client;
import no.unit.nva.nvi.search.SearchClient;
import no.unit.nva.nvi.search.SearchNviCandidatesHandler;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.eclipse.jetty.util.ajax.JSONDateConvertor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonData;
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
    private Client searchClient;
    private SearchNviCandidatesHandler handler;

    @BeforeEach
    void init() {
        this.output = new ByteArrayOutputStream();
        searchClient = mock(SearchClient.class);
        handler = new SearchNviCandidatesHandler(searchClient);
    }

    @Test
    void shouldReturnSomething() throws IOException {
        when(searchClient.search(any())).thenReturn(generateSearchResponse());
        handler.handleRequest(request("*"), output, CONTEXT);
        var response = GatewayResponse.fromOutputStream(output, Void.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_CREATED)));
    }

    private static SearchResponse<NviCandidateIndexDocument> generateSearchResponse() throws JsonProcessingException {
        return new SearchResponse.Builder<NviCandidateIndexDocument>()
                   .timedOut(false)
                   .took(15)
                   .shards(singleShard())
                   .hits(singleHit())
                   .build();
    }

    private static HitsMetadata<NviCandidateIndexDocument> singleHit() throws JsonProcessingException {
        return new HitsMetadata.Builder<NviCandidateIndexDocument>()
                   .hits(new Hit.Builder<NviCandidateIndexDocument>().index("nvi-candidates")
                             .id(randomString())
                             .fields(Map.of("key", JsonData.of(singleNviCandidateIndexDocument())))
                             .build())
                   .total(new TotalHits.Builder().value(1).relation(TotalHitsRelation.Eq).build())
                   .build();
    }

    private static ShardStatistics singleShard() {
        return new ShardStatistics.Builder().failed(0).successful(1).total(1).build();
    }

    private static Object singleNviCandidateIndexDocument() throws JsonProcessingException {
        var value = new NviCandidateIndexDocument(randomUri(), randomString(), randomString(), randomString(),
                                                  randomPublicationDetails(), List.of());
        return value;
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private InputStream request(String searchTerms) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withPathParameters(Map.of(QUERY, searchTerms))
                   .build();
    }
}

