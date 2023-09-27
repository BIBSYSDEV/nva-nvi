package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static no.sikt.nva.nvi.index.utils.PaginatedResultConverter.toPaginatedResult;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.ZonedDateTime;
import no.sikt.nva.nvi.common.db.model.InstanceType;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    public static final String QUERY_PARAM_AFFILIATIONS = "affiliations";
    public static final String QUERY_PARAM_FILTER = "filter";
    private static final String DEFAULT_FILTER = StringUtils.EMPTY_STRING;
    private static final String QUERY_SIZE_PARAM = "size";
    private static final String QUERY_OFFSET_PARAM = "offset";
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;
    private static final String SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY = "*";
    public static final String QUERY_PARAM_YEAR = "year";
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
    protected PaginatedSearchResult<NviCandidateIndexDocument> processInput(Void input, RequestInfo requestInfo,
                                                                            Context context)
        throws UnauthorizedException {

        var offset = extractQueryParamOffsetOrDefault(requestInfo);
        var size = extractQueryParamSizeOrDefault(requestInfo);
        var filter = extractQueryParamFilterOrDefault(requestInfo);
        var affiliations = extractQueryParamAffilitionsOrDefault(requestInfo);
        var customer = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        var username = requestInfo.getUserName();
        var year = extractQueryParamPublicationDateOrDefault(requestInfo);

        return attempt(() -> openSearchClient.search(affiliations, filter, username, year, customer, offset, size))
                   .map(searchResponse -> toPaginatedResult(searchResponse, affiliations, filter, offset, size))
                   .orElseThrow();
    }

    private String extractQueryParamPublicationDateOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PARAM_YEAR, String.valueOf(ZonedDateTime.now().getYear()));
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PaginatedSearchResult<NviCandidateIndexDocument> output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static Integer extractQueryParamSizeOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_SIZE_PARAM).map(Integer::parseInt).orElse(DEFAULT_QUERY_SIZE);
    }

    private static Integer extractQueryParamOffsetOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_OFFSET_PARAM)
                   .map(Integer::parseInt)
                   .orElse(DEFAULT_OFFSET_SIZE);
    }

    private static String extractQueryParamAffilitionsOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PARAM_AFFILIATIONS, null);
    }

    private static String extractQueryParamFilterOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(QUERY_PARAM_FILTER, DEFAULT_FILTER);
    }
}

