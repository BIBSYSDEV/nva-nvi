package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.index.Aggregations.AGGREGATIONS_MAP;
import static nva.commons.core.attempt.Try.attempt;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CachedValueProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.auth.CognitoCredentials;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JacocoGenerated
public class OpenSearchClient implements SearchClient<NviCandidateIndexDocument> {

    public static final String NVI_CANDIDATES_INDEX = "nvi-candidates";
    private static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchClient.class);
    private static final String ERROR_MSG_CREATE_INDEX = "Error while creating index: " + NVI_CANDIDATES_INDEX;
    public static final String INDEX_NOT_FOUND_EXCEPTION = "index_not_found_exception";
    private final org.opensearch.client.opensearch.OpenSearchClient client;
    private final CachedValueProvider<DecodedJWT> cachedJwtProvider;

    public OpenSearchClient(CachedJwtProvider cachedJwtProvider) {
        this.cachedJwtProvider = cachedJwtProvider;
        var httpHost = HttpHost.create(SEARCH_INFRASTRUCTURE_API_HOST);
        var restClient = RestClient.builder(httpHost).build();
        var options = RestClientOptions.builder()
                                       .addHeader(AUTHORIZATION, cachedJwtProvider.getValue().getToken())
                                       .build();
        var transport = new RestClientTransport(restClient, new JacksonJsonpMapper(), options);
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(transport);
    }

    public OpenSearchClient(RestClient restClient, CachedValueProvider<DecodedJWT> cachedValueProvider) {
        this.cachedJwtProvider = cachedValueProvider;
        this.client = new org.opensearch.client.opensearch.OpenSearchClient(
            new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    public static OpenSearchClient defaultOpenSearchClient() {
        var cognitoAuthenticator = new CognitoAuthenticator(HttpClient.newHttpClient(),
                                                            createCognitoCredentials(new SecretsReader()));
        var cachedJwtProvider = new CachedJwtProvider(cognitoAuthenticator, Clock.systemDefaultZone());
        return new OpenSearchClient(cachedJwtProvider);
    }

    @Override
    public void addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        if (!indexExists()) {
            createIndex();
        }
        attempt(() -> client.index(constructIndexRequest(indexDocument))).orElseThrow();
    }

    @Override
    public void removeDocumentFromIndex(NviCandidateIndexDocument indexDocument) throws IOException {
        client.withTransportOptions(getOptions()).delete(contructDeleteRequest(indexDocument));
    }

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(Query query) throws IOException {
        return client.withTransportOptions(getOptions())
                     .search(constructSearchRequest(query), NviCandidateIndexDocument.class);
    }

    @Override
    public void deleteIndex() throws IOException {
        client.withTransportOptions(getOptions())
              .indices()
              .delete(new DeleteIndexRequest.Builder().index(NVI_CANDIDATES_INDEX).build());
    }

    private static DeleteRequest contructDeleteRequest(NviCandidateIndexDocument indexDocument) {
        return new DeleteRequest.Builder().index(NVI_CANDIDATES_INDEX).id(indexDocument.identifier()).build();
    }

    private static IndexRequest<NviCandidateIndexDocument> constructIndexRequest(
        NviCandidateIndexDocument indexDocument) {
        return new IndexRequest.Builder<NviCandidateIndexDocument>().index(NVI_CANDIDATES_INDEX)
                                                                    .id(indexDocument.identifier())
                                                                    .document(indexDocument)
                                                                    .build();
    }

    private static CognitoCredentials createCognitoCredentials(SecretsReader secretsReader) {
        var credentials = secretsReader.fetchClassSecret(SEARCH_INFRASTRUCTURE_CREDENTIALS,
                                                         UsernamePasswordWrapper.class);
        return new CognitoCredentials(credentials::getUsername, credentials::getPassword,
                                      URI.create(SEARCH_INFRASTRUCTURE_AUTH_URI));
    }

    private static CreateIndexRequest getCreateIndexRequest() {
        return new CreateIndexRequest.Builder().index(NVI_CANDIDATES_INDEX).build();
    }

    private TransportOptions getOptions() {
        return RestClientOptions.builder().addHeader(AUTHORIZATION, cachedJwtProvider.getValue().getToken()).build();
    }

    //TODO change with .exists() when sws index handler is cleaned up.
    private boolean indexExists() {
        try {
            client.withTransportOptions(getOptions())
                  .indices()
                  .get(GetIndexRequest.of(r -> r.index(NVI_CANDIDATES_INDEX)));
        } catch (IOException io) {
            throw new RuntimeException(io);
        } catch (OpenSearchException osex) {
            if (osex.status() == 404 && INDEX_NOT_FOUND_EXCEPTION.equals(osex.error().type())) {
                return false;
            }
            throw osex;
        }
        return true;
    }

    private void createIndex() {
        attempt(() -> client.withTransportOptions(getOptions())
                            .indices()
                            .create(getCreateIndexRequest()))
            .orElseThrow(failure -> handleFailure(ERROR_MSG_CREATE_INDEX, failure.getException()));
    }

    private RuntimeException handleFailure(String msg, Exception exception) {
        LOGGER.error(msg, exception);
        return new RuntimeException(exception.getMessage());
    }

    private SearchRequest constructSearchRequest(Query query) {
        return new SearchRequest.Builder().index(NVI_CANDIDATES_INDEX)
                                          .query(query)
                                          .aggregations(AGGREGATIONS_MAP)
                                          .build();
    }
}
