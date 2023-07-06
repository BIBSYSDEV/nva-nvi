package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.OpenSearchIndexClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SearchNviCandidatesHandlerTest {

    public static final String QUERY = "query";
    public static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    private static OpenSearchIndexClient indexClient;
    private static RestClient restClient;
    private static SearchNviCandidatesHandler handler;
    private static ByteArrayOutputStream output;
    private final Context context = mock(Context.class);

    public static void setUpTestContainer() {
        container.start();
        restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeAll
    static void init() {
        setUpTestContainer();
        output = new ByteArrayOutputStream();
        indexClient = new OpenSearchIndexClient(restClient);
        handler = new SearchNviCandidatesHandler(new OpenSearchClient(restClient));
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }

    @Test
    void shouldReturnDocumentFromIndex() throws IOException, InterruptedException {
        insertDocument(singleNviCandidateIndexDocument());
        handler.handleRequest(request("*"), output, context);
        var response = GatewayResponse.fromOutputStream(output, SearchResponseDto.class);
        var hits = response.getBodyObject(SearchResponseDto.class).hits();
        assertThat(hits, hasSize(1));
    }

    @Test
    void shouldReturnDocumentFromIndexContainingSingleHitWhenUsingTerms() throws IOException, InterruptedException {
        insertDocument(singleNviCandidateIndexDocument());
        var secondDocumentFromIndex = singleNviCandidateIndexDocument();
        insertDocument(secondDocumentFromIndex);
        handler.handleRequest(request(secondDocumentFromIndex.identifier()), output, context);
        var response = GatewayResponse.fromOutputStream(output, SearchResponseDto.class);
        var hits = response.getBodyObject(SearchResponseDto.class).hits();
        assertThat(hits, hasSize(1));
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomString(), randomString(),
                                             randomPublicationDetails(), List.of());
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }

    private void insertDocument(NviCandidateIndexDocument document) throws InterruptedException {
        indexClient.addDocumentToIndex(document);
        Thread.sleep(2000);
    }

    private InputStream request(String searchTerm) throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(JsonUtils.dtoObjectMapper)
                   .withQueryParameters(Map.of(QUERY, searchTerm))
                   .build();
    }
}

