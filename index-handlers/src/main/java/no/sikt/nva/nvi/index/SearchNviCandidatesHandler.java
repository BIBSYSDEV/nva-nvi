package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
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
            return SearchResponseDto.fromSearchResponse(openSearchResponse);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, SearchResponseDto output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static String getSearchTerm(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(SEARCH_TERM_KEY, SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY);
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

