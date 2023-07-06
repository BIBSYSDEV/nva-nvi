package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.common.ApplicationConstants.SEARCH_INFRASTRUCTURE_AUTH_URI;
import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import no.sikt.nva.nvi.common.model.UsernamePasswordWrapper;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import no.unit.nva.auth.CognitoCredentials;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.JacocoGenerated;
import nva.commons.secrets.SecretsReader;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<Void, SearchResponseDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchNviCandidatesHandler.class);
    private static final String SEARCH_INFRASTRUCTURE_CREDENTIALS = "SearchInfrastructureCredentials";
    private static final String SEARCH_TERM_KEY = "query";
    private static final String SEARCH_ALL_PUBLICATIONS_DEFAULT_QUERY = "*";
    private final SearchClient<NviCandidateIndexDocument> openSearchSearchSearchClient;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        this.openSearchSearchSearchClient = defaultOpenSearchClient();
    }

    public SearchNviCandidatesHandler(SearchClient<NviCandidateIndexDocument> openSearchSearchSearchClient) {
        super(Void.class);
        this.openSearchSearchSearchClient = openSearchSearchSearchClient;
    }

    @Override
    protected SearchResponseDto processInput(Void input, RequestInfo requestInfo,
                                             Context context) {
        var query = contructQuery(requestInfo);
        try {
            var openSearchResponse = openSearchSearchSearchClient.search(query);
            LOGGER.info("Response {}", openSearchResponse.toString());
            return SearchResponseDto.fromSearchResponse(openSearchResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

