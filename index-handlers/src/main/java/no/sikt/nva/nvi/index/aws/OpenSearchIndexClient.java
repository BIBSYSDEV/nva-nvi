package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static nva.commons.core.attempt.Try.attempt;
import java.io.IOException;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

@JacocoGenerated
//TODO: Handle test coverage
public class OpenSearchIndexClient implements IndexClient<NviCandidateIndexDocument> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchIndexClient.class);
    private static final String INDEX = "nvi-candidates";
    private static final String ERROR_MSG_CREATE_INDEX = "Error while creating index: " + INDEX;
    private static final String ERROR_MSG_INDEX_EXISTS = "Error while checking if index exists: " + INDEX;
    private final OpenSearchClient openSearchClient;

    public OpenSearchIndexClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider, Region region) {
        //        var transport = new AwsSdk2Transport(
        //            ApacheHttpClient.builder().build(),
        //            openSearchEndpoint,
        //            region,
        //            transportOptionWithToken("Bearer " + cachedJwtProvider.getValue().getToken())
        //        );
        var httpHost = HttpHost.create(openSearchEndpoint);
        var restClient = RestClient.builder(httpHost).build();
        var options = RestClientOptions.builder()
                          .addHeader(HttpHeaders.AUTHORIZATION, cachedJwtProvider.getValue().getToken())
                          .build();
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(), options);
        this.openSearchClient = new OpenSearchClient(transport);
        if (!indexExists()) {
            createIndex();
        }
    }

    public OpenSearchIndexClient(RestClient restClient) {
        this.openSearchClient = new org.opensearch.client.opensearch.OpenSearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @Override
    public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        attempt(() -> openSearchClient.index(constructIndexRequest(indexDocument))).orElseThrow();
    }

    private static IndexRequest<NviCandidateIndexDocument> constructIndexRequest(
        NviCandidateIndexDocument indexDocument) {
        return new IndexRequest.Builder<NviCandidateIndexDocument>().index(INDEX)
                   .id(indexDocument.getIdentifier())
                   .document(indexDocument)
                   .build();
    }

    private static AwsSdk2TransportOptions transportOptionWithToken(String token) {
        return AwsSdk2TransportOptions.builder().addHeader(AUTHORIZATION, token).build();
    }

    private boolean indexExists() {
        return attempt(() -> indexExists(INDEX))
                   .orElseThrow(failure -> handleFailure(ERROR_MSG_INDEX_EXISTS, failure.getException()));
    }

    private boolean indexExists(String indexName) throws IOException {
        var res = openSearchClient.indices().exists(ExistsRequest.of(s -> s.index(indexName)));
        LOGGER.info("Index exists response: {}", res.value());
        return res.value();
    }

    private void createIndex() {
        attempt(() -> openSearchClient.indices().create(new CreateIndexRequest.Builder().index(INDEX).build()))
            .orElseThrow(failure -> handleFailure(ERROR_MSG_CREATE_INDEX, failure.getException()));
    }

    private RuntimeException handleFailure(String msg, Exception exception) {
        LOGGER.error(msg, exception);
        return new RuntimeException(exception.getMessage());
    }
}
