package no.unit.nva.nvi.search;

import static no.sikt.nva.nvi.common.ApplicationConstants.REGION;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_API_URI;
import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import no.sikt.nva.nvi.common.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.unit.nva.auth.CachedJwtProvider;
import no.unit.nva.auth.CognitoAuthenticator;
import no.unit.nva.auth.CognitoCredentials;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.nvi.search.model.SearchResponseDto;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermsSetQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.HitsMetadata;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<Void, HitsMetadata<NviCandidateIndexDocument>> {

    private static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    private static final String SEARCH_TERM_KEY = "query";
    private static final String SEARCH_ALL_PUBLICATIONS_DEFAULT_QUERY = "*";
    private final Client openSearchSearchClient;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        var cognitoAuthenticator = new CognitoAuthenticator(HttpClient.newHttpClient(),
                                                            createCognitoCredentials(new SecretsReader()));
        var cachedJwtProvider = new CachedJwtProvider(cognitoAuthenticator, Clock.systemDefaultZone());
        this.openSearchSearchClient = new SearchClient(SEARCH_INFRASTRUCTURE_API_URI, cachedJwtProvider, REGION);
    }

    public SearchNviCandidatesHandler(Client openSearchSearchClient) {
        super(Void.class);
        this.openSearchSearchClient = openSearchSearchClient;
    }

    @Override
    protected HitsMetadata<NviCandidateIndexDocument> processInput(Void input, RequestInfo requestInfo,
                                                                   Context context) {
        var query = contructQuery(requestInfo);
        var result = attempt(() -> openSearchSearchClient.search(query)).orElseThrow();
        var searchResponse = SearchResponseDto.fromSearchResponse(result)
        var string = attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(result.hits())).orElseThrow();
        return result;
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, HitsMetadata<NviCandidateIndexDocument> output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static TermsSetQuery getTerms(RequestInfo requestInfo) {
        return new TermsSetQuery.Builder()
                   .field(getField(requestInfo))
                   .terms("*")
                   .build();
    }

    private static String getField(RequestInfo requestInfo) {
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

    private Query contructQuery(RequestInfo requestInfo) {
        return new Query.Builder()
                   .termsSet(getTerms(requestInfo))
                   .build();
    }
}

