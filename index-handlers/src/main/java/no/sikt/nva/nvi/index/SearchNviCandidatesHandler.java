package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.ApplicationConstants.REGION;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.aws.OpenSearchClient;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.auth.CognitoCredentials;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<Void, SearchResponseDto> {

    private static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    private static final String SEARCH_TERM_KEY = "query";
    private static final String SEARCH_ALL_PUBLICATIONS_DEFAULT_QUERY = "*";
    private static final String SEARCH_INFRASTRUCTURE_API_URI = new Environment().readEnv(
        "SEARCH_INFRASTRUCTURE_API_URI");
    private final SearchClient openSearchSearchSearchClient;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        var cognitoAuthenticator = new CognitoAuthenticator(HttpClient.newHttpClient(),
                                                            createCognitoCredentials(new SecretsReader()));
        var cachedJwtProvider = new CachedJwtProvider(cognitoAuthenticator, Clock.systemDefaultZone());
        this.openSearchSearchSearchClient = new OpenSearchClient(SEARCH_INFRASTRUCTURE_API_URI, cachedJwtProvider,
                                                                 REGION);
    }

    public SearchNviCandidatesHandler(SearchClient openSearchSearchSearchClient) {
        super(Void.class);
        this.openSearchSearchSearchClient = openSearchSearchSearchClient;
    }

    @Override
    protected SearchResponseDto processInput(Void input, RequestInfo requestInfo,
                                             Context context) {
        return attempt(() -> contructQuery(requestInfo))
                   .map(openSearchSearchSearchClient::search)
                   .map(SearchResponseDto::fromSearchResponse)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, SearchResponseDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static String getSearchTerm(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(SEARCH_TERM_KEY, SEARCH_ALL_PUBLICATIONS_DEFAULT_QUERY);
    }

    @JacocoGenerated
    private static CognitoCredentials createCognitoCredentials(SecretsReader secretsReader) {
        var credentials = secretsReader.fetchClassSecret(SEARCH_INFRASTRUCTURE_CREDENTIALS,
                                                         UsernamePasswordWrapper.class);
        return new CognitoCredentials(credentials::getUsername, credentials::getPassword,
                                      URI.create(SEARCH_INFRASTRUCTURE_AUTH_URI));
    }

    private static QueryStringQuery constructQuery(RequestInfo requestInfo) {
        return new QueryStringQuery.Builder()
                   .query(getSearchTerm(requestInfo))
                   .build();
    }

    private Query contructQuery(RequestInfo requestInfo) {
        return new Query.Builder()
                   .queryString(constructQuery(requestInfo))
                   .build();
    }
}

