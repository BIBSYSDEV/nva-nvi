package no.sikt.nva.nvi.index;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.PublicationDetails;
import org.apache.http.HttpHost;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.testcontainers.OpensearchContainer;

public class OpenSearchClientTest {

    public static final String OPEN_SEARCH_IMAGE = "opensearchproject/opensearch:2.0.0";
    private static final OpensearchContainer container = new OpensearchContainer(OPEN_SEARCH_IMAGE);
    private static RestClient restClient;
    private static OpenSearchClient openSearchClient;

    public static void setUpTestContainer() {
        container.start();
        restClient = RestClient.builder(HttpHost.create(container.getHttpHostAddress())).build();
    }

    @BeforeAll
    static void init() {
        setUpTestContainer();
        openSearchClient = new OpenSearchClient(restClient);
    }

    @AfterAll
    static void afterAll() {
        container.stop();
    }

    @Test
    void shouldCreateIndexAndAddDocumentToIndexWhenIndexDoesNotExist() throws IOException, InterruptedException {
        var indexDocument = singleNviCandidateIndexDocument();
        openSearchClient.addDocumentToIndex(indexDocument);
        Thread.sleep(2000);
        var searchResponse = openSearchClient.search(searchTermToQuery(indexDocument.identifier()));
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, containsInAnyOrder(indexDocument));
    }

    @Test
    void shouldReturnUniqueDocumentFromIndexWhenSearchingByDocumentIdentifier()
        throws InterruptedException, IOException {
        openSearchClient.addDocumentToIndex(singleNviCandidateIndexDocument());
        openSearchClient.addDocumentToIndex(singleNviCandidateIndexDocument());
        var indexDocument = singleNviCandidateIndexDocument();
        openSearchClient.addDocumentToIndex(indexDocument);
        Thread.sleep(2000);
        var searchResponse = openSearchClient.search(searchTermToQuery(indexDocument.identifier()));
        var nviCandidateIndexDocument = searchResponseToIndexDocumentList(searchResponse);
        assertThat(nviCandidateIndexDocument, hasSize(1));
    }

    @Test
    void shouldDeleteIndexAndThrowExceptionWhenSearchingInNonExistentIndex() throws IOException {
        var indexDocument = singleNviCandidateIndexDocument();
        openSearchClient.addDocumentToIndex(indexDocument);
        openSearchClient.deleteIndex();
        assertThrows(OpenSearchException.class,
                     () -> openSearchClient.search(searchTermToQuery(indexDocument.identifier())));
    }

    @NotNull
    private static List<NviCandidateIndexDocument> searchResponseToIndexDocumentList(
        SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream().map(Hit::source).toList();
    }

    private static Query searchTermToQuery(String searchTerm) {
        return new Query.Builder()
                   .queryString(constructQuery(searchTerm))
                   .build();
    }

    private static QueryStringQuery constructQuery(String searchTerm) {
        return new QueryStringQuery.Builder()
                   .query(searchTerm)
                   .build();
    }

    private static NviCandidateIndexDocument singleNviCandidateIndexDocument() {
        return new NviCandidateIndexDocument(randomUri(), randomString(), randomString(), randomString(),
                                             randomPublicationDetails(), List.of());
    }

    private static PublicationDetails randomPublicationDetails() {
        return new PublicationDetails(randomString(), randomString(), randomString(), randomString(), List.of());
    }
}
