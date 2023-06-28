package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static nva.commons.core.attempt.Try.attempt;
import java.io.IOException;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

@JacocoGenerated
//TODO: Handle test coverage
public class OpenSearchIndexClient implements IndexClient<NviCandidateIndexDocument> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchIndexClient.class);
    private static final String INDEX = "nvi-candidates";
    private final OpenSearchClient openSearchClient;

    public OpenSearchIndexClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider, Region region)
        throws IOException {
        this.openSearchClient = new OpenSearchClient(
            new AwsSdk2Transport(
                ApacheHttpClient.builder().build(),
                openSearchEndpoint,
                region,
                transportOptionWithToken("Bearer " + cachedJwtProvider.getValue().getToken())
            )
        );
        if (!indexExists()) {
            createIndex();
        }
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

    private boolean indexExists() throws IOException {
        try {
            return openSearchClient.indices()
                       .exists(ExistsRequest.of(s -> s.index(INDEX)))
                       .value();
        } catch (IOException | OpenSearchException e) {
            LOGGER.error("Error while checking if index {} exists. Error: {}", INDEX, e.getMessage());
            throw e;
        }
    }

    private void createIndex() throws IOException {
        try {
            openSearchClient.indices()
                .create(new CreateIndexRequest.Builder().index(INDEX).build());
        } catch (IOException | OpenSearchException e) {
            LOGGER.error("Error while creating index {}. Error: {}", INDEX, e.getMessage());
            throw e;
        }
    }
}
