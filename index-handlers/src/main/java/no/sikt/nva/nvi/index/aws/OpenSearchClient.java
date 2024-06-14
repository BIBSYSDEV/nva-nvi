package no.sikt.nva.nvi.index.aws;

import static com.amazonaws.auth.internal.SignerConstants.AUTHORIZATION;
import static no.sikt.nva.nvi.index.utils.SearchConstants.MAPPINGS;
import static no.sikt.nva.nvi.index.utils.SearchConstants.NVI_CANDIDATES_INDEX;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_API_HOST;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.index.utils.SearchConstants.SEARCH_INFRASTRUCTURE_CREDENTIALS;
import static nva.commons.core.attempt.Try.attempt;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Function;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.Aggregations;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.search.CandidateSearchParameters;
import no.sikt.nva.nvi.index.model.search.SearchResultParameters;
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
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldSort.Builder;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.GetIndexRequest;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.rest_client.RestClientOptions;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.util.ObjectBuilder;
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
    public IndexResponse addDocumentToIndex(NviCandidateIndexDocument indexDocument) {
        return attempt(() -> client.withTransportOptions(getOptions()).index(constructIndexRequest(indexDocument)))
                   .map(indexResponse -> {
                       LOGGER.info("Indexed document from index: {}", indexDocument.identifier());
                       return indexResponse;
                   })
                   .orElseThrow(
                       failure -> handleFailure("Failed to add/update document from index", failure.getException()));
    }

    @Override
    public DeleteResponse removeDocumentFromIndex(UUID identifier) {
        return attempt(() -> client.withTransportOptions(getOptions()).delete(contructDeleteRequest(
            identifier)))
                   .map(deleteResponse -> {
                       LOGGER.info("Removing document from index: {}", identifier);
                       return deleteResponse;
                   })
                   .orElseThrow(
                       failure -> handleFailure("Failed to remove document from index", failure.getException()));
    }

    @Override
    public SearchResponse<NviCandidateIndexDocument> search(CandidateSearchParameters candidateSearchParameters)
        throws IOException {
        logSearchRequest(candidateSearchParameters);
        return client.withTransportOptions(getOptions()).search(constructSearchRequest(candidateSearchParameters),
                                                                NviCandidateIndexDocument.class);
    }

    @Override
    public void deleteIndex() throws IOException {
        client.withTransportOptions(getOptions())
            .indices()
            .delete(new DeleteIndexRequest.Builder().index(NVI_CANDIDATES_INDEX).build());
    }

    //TODO change with .exists() when sws index handler is cleaned up.
    public boolean indexExists() {
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

    public void createIndex() {
        attempt(() -> client.withTransportOptions(getOptions())
                          .indices()
                          .create(getCreateIndexRequest()))
            .orElseThrow(failure -> handleFailure(ERROR_MSG_CREATE_INDEX, failure.getException()));
    }

    private static void logSearchRequest(CandidateSearchParameters params) {
        LOGGER.info("Generating search request with affiliationIdentifiers: {}, excludeSubUnits: {}, filter: {}, username: {}, "
                    + "topLevelCristinOrg: {}, offset: "
                    + "{}, size: {}", params.affiliationIdentifiers(), params.excludeSubUnits(), params.filter(),
                    params.username(), params.topLevelCristinOrg(), params.searchResultParameters().offset(),
                    params.searchResultParameters().size());
    }

    private static DeleteRequest contructDeleteRequest(UUID identifier) {
        return new DeleteRequest.Builder().index(NVI_CANDIDATES_INDEX)
                   .id(identifier.toString())
                   .build();
    }

    private static IndexRequest<NviCandidateIndexDocument> constructIndexRequest(
        NviCandidateIndexDocument indexDocument) {
        return new IndexRequest.Builder<NviCandidateIndexDocument>().index(NVI_CANDIDATES_INDEX)
                   .id(indexDocument.identifier().toString())
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
        return new CreateIndexRequest.Builder().mappings(MAPPINGS).index(NVI_CANDIDATES_INDEX).build();
    }

    private static SortOptions getSortOptions(CandidateSearchParameters parameters) {
        var resultParameters = parameters.searchResultParameters();
        return new SortOptions.Builder()
                   .field(FieldSort.of(fieldSortBuilderFunction(resultParameters)))
                   .build();
    }

    private static Function<Builder, ObjectBuilder<FieldSort>> fieldSortBuilderFunction(
        SearchResultParameters resultParameters) {
        return builder -> builder.field(resultParameters.orderBy()).order(getSortOrder(resultParameters.sortOrder()));
    }

    private static SortOrder getSortOrder(String sortOrder) {
        return SortOrder.Asc.jsonValue().equalsIgnoreCase(sortOrder) ? SortOrder.Asc : SortOrder.Desc;
    }

    private TransportOptions getOptions() {
        return RestClientOptions.builder().addHeader(AUTHORIZATION, cachedJwtProvider.getValue().getToken()).build();
    }

    private RuntimeException handleFailure(String msg, Exception exception) {
        LOGGER.error(msg, exception);
        return new RuntimeException(exception.getMessage());
    }

    private SearchRequest constructSearchRequest(CandidateSearchParameters parameters) {
        var query = SearchConstants.constructQuery(parameters);
        var resultParameters = parameters.searchResultParameters();
        var sortOptions = getSortOptions(parameters);
        return new SearchRequest.Builder()
                   .index(NVI_CANDIDATES_INDEX)
                   .query(query)
                   .sort(sortOptions)
                   .aggregations(Aggregations.generateAggregations(parameters.aggregationType(),
                                                                   parameters.username(),
                                                                   parameters.topLevelOrgUriAsString()))
                   .from(resultParameters.offset())
                   .size(resultParameters.size())
                   .build();
    }
}
