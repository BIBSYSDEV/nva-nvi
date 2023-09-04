package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.sikt.nva.nvi.index.model.SearchResponseDto;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<Void, SearchResponseDto> {

    private static final String QUERY_PATH_PARAM = "query";
    private static final String SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY = "*";
    public static final String FILTER_QUERY_PARAM = "filter";
    private final SearchClient<NviCandidateIndexDocument> openSearchClient;

    @JacocoGenerated
    public SearchNviCandidatesHandler() {
        super(Void.class);
        this.openSearchClient = defaultOpenSearchClient();
    }

    public SearchNviCandidatesHandler(SearchClient<NviCandidateIndexDocument> openSearchClient) {
        super(Void.class);
        this.openSearchClient = openSearchClient;
    }

    @Override
    protected SearchResponseDto processInput(Void input, RequestInfo requestInfo,
                                             Context context) throws UnauthorizedException {
        var customer = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        var username = requestInfo.getUserName();
        var filter = getFilter(requestInfo);
        var searchTerm = getSearchTerm(requestInfo);

        return attempt(() -> openSearchClient.search(searchTerm, filter, username, customer))
                   .map(SearchResponseDto::fromSearchResponse)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, SearchResponseDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static String getSearchTerm(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PATH_PARAM, SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY);
    }

    private static String getFilter(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(FILTER_QUERY_PARAM, SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY);
    }
}

