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
import nva.commons.core.JacocoGenerated;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;

public class SearchNviCandidatesHandler extends ApiGatewayHandler<Void, SearchResponseDto> {

    private static final String SEARCH_TERM_KEY = "query";
    private static final String SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY = "*";
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
                                             Context context) {
        return attempt(() -> contructQuery(requestInfo))
                   .map(openSearchClient::search)
                   .map(SearchResponseDto::fromSearchResponse)
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, SearchResponseDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static String getSearchTerm(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(SEARCH_TERM_KEY, SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY);
    }

    private static QueryStringQuery constructQueryStringQuery(RequestInfo requestInfo) {
        return new QueryStringQuery.Builder()
                   .query(getSearchTerm(requestInfo))
                   .build();
    }

    private Query contructQuery(RequestInfo requestInfo) {
        return new Query.Builder()
                   .queryString(constructQueryStringQuery(requestInfo))
                   .build();
    }
}

