package no.sikt.nva.nvi.index.aws;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import java.io.IOException;
import no.sikt.nva.nvi.index.SearchNviCandidatesHandler;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

@JacocoGenerated
public class OpenSearchClient implements SearchClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    private final org.opensearch.client.opensearch.OpenSearchClient client;

    public OpenSearchClient(String openSearchEndpoint, CachedJwtProvider cachedJwtProvider, Region region) {
        LOGGER.info("Initiating client");
        var httpHost = HttpHost.create(openSearchEndpoint);
        var restClient = RestClient.builder(httpHost).build();
        LOGGER.info("1");
        var options = RestClientOptions.builder()
                          .addHeader(HttpHeaders.AUTHORIZATION, cachedJwtProvider.getValue().getToken())
                          .build();
        LOGGER.info("2");
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(), options);
        LOGGER.info("3");
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(transport);
        LOGGER.info("Client initiated");
    }

    public OpenSearchClient(RestClient restClient) {
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException {
        LOGGER.info("Performing search");
        return client.search(constructSearchRequest(query), NviCandidateIndexDocument.class);
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
