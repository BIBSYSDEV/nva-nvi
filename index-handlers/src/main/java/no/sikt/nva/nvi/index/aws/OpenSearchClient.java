package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_CREDENTIALS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.mappings;
import static nva.commons.core.attempt.Try.attempt;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.Aggregations;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.utils.SearchConstants;
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

    private static final String INDEX_NOT_FOUND_EXCEPTION = "index_not_found_exception";
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchClient.class);
    private static final String ERROR_MSG_CREATE_INDEX = "Error while creating index: " + NVI_CANDIDATES_INDEX;
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
            LOGGER.info("Creating index");
            createIndex();
        }
        attempt(() -> client.withTransportOptions(getOptions()).index(constructIndexRequest(indexDocument)))
            .map(indexResponse -> {
                LOGGER.info("Adding document to index: {}", indexDocument.identifier());
                return indexDocument;
            })
            .orElseThrow(failure -> handleFailure("Failed to add/update document from index", failure.getException()));
    }

    @Override
    public void removeDocumentFromIndex(NviCandidateIndexDocument indexDocument) {
        attempt(() -> client.withTransportOptions(getOptions()).delete(contructDeleteRequest(indexDocument)))
            .map(deleteResponse -> {
                LOGGER.info("Removing document from index: {}", indexDocument.identifier());
                return deleteResponse;
            })
            .orElseThrow(failure -> handleFailure("Failed to remove document from index", failure.getException()));
    }

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(String institutions,
                                                            String filter,
                                                            String username,
                                                            URI customer,
                                                            int offset,
                                                            int size)
        throws IOException {
        logSearchRequest(institutions, filter, username, customer, offset, size);
        return client.withTransportOptions(getOptions())
                   .search(constructSearchRequest(institutions, filter, username, customer.toString(), offset, size),
                           NviCandidateIndexDocument.class);



    }

    @Override
    public void deleteIndex() throws IOException {
        client.withTransportOptions(getOptions())
            .indices()
            .delete(new DeleteIndexRequest.Builder().index(NVI_CANDIDATES_INDEX).build());
    }

    private static void logSearchRequest(String searchTerm, String filter, String username, URI customer, int offset,
                                         int size) {
        LOGGER.info("Generating search request with searchTerm: {}, filter: {}, username: {}, customer: {}, offset: "
                    + "{}, size: {}", searchTerm, filter, username, customer.toString(), offset, size);
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
        return new CreateIndexRequest.Builder().mappings(mappings()).index(NVI_CANDIDATES_INDEX).build();
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

    private SearchRequest constructSearchRequest(String institutions, String filter, String username, String customer,
                                                 int offset, int size) {
        var query = SearchConstants.constructQuery(institutions, filter, username, customer);
        return new SearchRequest.Builder()
                   .index(NVI_CANDIDATES_INDEX)
                   .query(query)
                   .aggregations(Aggregations.generateAggregations(username, customer))
                   .from(offset)
                   .size(size)
                   .build();
    }
}
