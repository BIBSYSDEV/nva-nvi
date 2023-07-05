package no.sikt.nva.nvi.index.aws;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

@JacocoGenerated
public class OpenSearchClient implements SearchClient {

    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchClient.class);

    private final org.opensearch.client.opensearch.OpenSearchClient openSearchClient;

    public OpenSearchClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider, Region region) {
        this.openSearchClient = new org.opensearch.client.opensearch.OpenSearchClient(
            new AwsSdk2Transport(
                ApacheHttpClient.builder().build(),
                openSearchEndpoint,
                region,
                transportOptionWithToken("Bearer " + cachedJwtProvider.getValue().getToken())
            )
        );
    }

    public OpenSearchClient(RestClient restClient) {
        this.openSearchClient = new org.opensearch.client.opensearch.OpenSearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException {
        return openSearchClient.search(constructSearchRequest(query), NviCandidateIndexDocument.class);
    }

    private static AwsSdk2TransportOptions transportOptionWithToken(String token) {
        return AwsSdk2TransportOptions.builder().addHeader(AUTHORIZATION, token).build();
    }

    private SearchRequest constructSearchRequest(Query query) {
        return new SearchRequest.Builder()
                   .index(NVI_CANDIDATES_INDEX)
                   .query(query)
                   .build();
    }
}
