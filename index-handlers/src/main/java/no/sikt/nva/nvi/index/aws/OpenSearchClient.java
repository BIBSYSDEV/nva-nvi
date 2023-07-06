package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import java.io.IOException;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.rest_client.RestClientOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;

@JacocoGenerated
public class OpenSearchClient implements SearchClient {

    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    private final org.opensearch.client.opensearch.OpenSearchClient client;

    public OpenSearchClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider) {
        var httpHost = HttpHost.create(openSearchEndpoint);
        var restClient = RestClient.builder(httpHost).build();
        var options = RestClientOptions.builder()
                          .addHeader(AUTHORIZATION, cachedJwtProvider.getValue().getToken())
                          .build();
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(), options);
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(transport);
    }

    public OpenSearchClient(RestClient restClient) {
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException {
        return client.search(constructSearchRequest(query), NviCandidateIndexDocument.class);
    }

    private SearchRequest constructSearchRequest(Query query) {
        return new SearchRequest.Builder()
                   .index(NVI_CANDIDATES_INDEX)
                   .query(query)
                   .build();
    }
}
