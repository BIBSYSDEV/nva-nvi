package no.sikt.nva.nvi.index.aws;

import static no.sikt.nva.nvi.common.ApplicationConstants.OPENSEARCH_ENDPOINT;
import static nva.commons.core.attempt.Try.attempt;
import no.sikt.nva.nvi.common.IndexClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

public class OpenSearchIndexClient implements IndexClient<NviCandidateIndexDocument> {

    public static final String INDEX = "nviCandidates";

    //TODO: replace with region from nvi-commons
    private static final Region OPENSERCH_REGION = Region.US_WEST_2;
    private final OpenSearchClient openSearchClient;

    public OpenSearchIndexClient() {
        this.openSearchClient = new OpenSearchClient(
            new AwsSdk2Transport(
                ApacheHttpClient.builder().build(),
                OPENSEARCH_ENDPOINT,
                OPENSERCH_REGION,
                AwsSdk2TransportOptions.builder().build()
            )
        );
    }

    @Override
    public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        attempt(() -> openSearchClient.index(constructIndexRequest(indexDocument)));
    }

    private static IndexRequest<NviCandidateIndexDocument> constructIndexRequest(
        NviCandidateIndexDocument indexDocument) {
        return new IndexRequest.Builder<NviCandidateIndexDocument>().index(INDEX)
                   .id(indexDocument.identifier())
                   .document(indexDocument)
                   .build();
    }
}
