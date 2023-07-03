package no.unit.nva.nvi.search;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import com.amazonaws.regions.Region;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

public class Client {

    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    private final OpenSearchClient openSearchClient;

    public OpenSearchIndexClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider, Region region) {
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

    public SearchResponse<NviCandidateIndexDocument> search() throws IOException {
        return openSearchClient.search(constructSearchRequest(), NviCandidateIndexDocument.class);
    }

    private static AwsSdk2TransportOptions transportOptionWithToken(String token) {
        return AwsSdk2TransportOptions.builder().addHeader(AUTHORIZATION, token).build();
    }

    private SearchRequest constructSearchRequest() {
        return new SearchRequest.Builder()
                   .index(List.of(NVI_CANDIDATES_INDEX))
                   .build();
    }
}
