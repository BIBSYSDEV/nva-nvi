package no.sikt.nva.nvi.index;

import static no.sikt.nva.nvi.index.aws.OpenSearchClient.defaultOpenSearchClient;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.index.aws.SearchClient;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.UnauthorizedException;
import nva.commons.apigateway.exceptions.UnprocessableContentException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

public class SearchNviCandidatesHandler
    extends ApiGatewayHandler<Void, PaginatedSearchResult<NviCandidateIndexDocument>> {

    public static final Environment ENVIRONMENT = new Environment();
    private static final String HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    private static final String QUERY_SIZE_PARAM = "size";
    private static final String QUERY_OFFSET_PARAM = "offset";
    private static final int DEFAULT_QUERY_SIZE = 10;
    private static final int DEFAULT_OFFSET_SIZE = 0;
    private static final String WORD_ENDING_WITH_HASHTAG_REGEX = "[A-za-z0-9]*#";
    private static final Map<String, String> AGGREGATION_FIELDS_TO_CHANGE = Map.of(
        "doc_count_error_upper_bound", "docCountErrorUpperBound",
        "sum_other_doc_count", "sumOtherDocCount",
        "doc_count", "docCount");
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

    public static PaginatedSearchResult<NviCandidateIndexDocument> toPaginatedSearchResult(
        SearchResponse<NviCandidateIndexDocument> searchResponse, int offset, int size, String searchTerm)
        throws UnprocessableContentException {
        return PaginatedSearchResult.create(constructBaseUri(),
                                            offset,
                                            size,
                                            extractTotalNumberOfHits(searchResponse),
                                            extractsHits(searchResponse),
                                            Map.of(SEARCH_TERM_KEY, searchTerm),
                                            extractAggregations(searchResponse));
    }

    @Override
    protected PaginatedSearchResult<NviCandidateIndexDocument> processInput(Void input, RequestInfo requestInfo,
                                                                            Context context)
        throws UnauthorizedException {

        var offset = extractQueryParamOffsetOrDefault(requestInfo);
        var size = extractQueryParamSizeOrDefault(requestInfo);
        var searchTerm = extractQueryParamSearchTermOrDefault(requestInfo);
        var customer = requestInfo.getTopLevelOrgCristinId().orElseThrow();
        var username = requestInfo.getUserName();
        return attempt(() -> contructQuery(requestInfo))
                   .map(query -> openSearchClient.search(query, offset, size, username, customer))
                   .map(searchResponse -> toPaginatedSearchResult(searchResponse, offset, size, searchTerm))
                   .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PaginatedSearchResult<NviCandidateIndexDocument> output) {
        return HttpURLConnection.HTTP_OK;
    }

    private static int extractTotalNumberOfHits(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return (int) searchResponse.hits().total().value();
    }

    private static Integer extractQueryParamSizeOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_SIZE_PARAM).map(Integer::parseInt).orElse(DEFAULT_QUERY_SIZE);
    }

    private static Integer extractQueryParamOffsetOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameterOpt(QUERY_OFFSET_PARAM)
                   .map(Integer::parseInt)
                   .orElse(DEFAULT_OFFSET_SIZE);
    }

    private static List<NviCandidateIndexDocument> extractsHits(
        SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream().map(Hit::source).toList();
    }

    private static URI constructBaseUri() {
        return UriWrapper.fromUri(HOST).addChild(CUSTOM_DOMAIN_BASE_PATH).getUri();
    }

    private static JsonNode extractAggregations(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();

        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(searchResponse, generator);
        }

        var json = attempt(() -> JsonUtils.dtoObjectMapper.readTree(writer.toString())).orElseThrow();
        var aggregations = (ObjectNode) json.get("aggregations");

        if (aggregations == null) {
            return null;
        }

        return formatAggregations(aggregations);
    }

    private static JsonNode formatAggregations(JsonNode aggregations) {
        var outputAggregationNode = JsonUtils.dtoObjectMapper.createObjectNode();

        var iterator = aggregations.fields();
        while (iterator.hasNext()) {
            var nodeEntry = iterator.next();
            var fieldName = nodeEntry.getKey();

            Optional<String> newName = Optional.ofNullable(AGGREGATION_FIELDS_TO_CHANGE.get(fieldName));
            if (newName.isEmpty()) {
                newName = Optional.of(fieldName.replaceFirst(WORD_ENDING_WITH_HASHTAG_REGEX, ""));
            }

            var value = nodeEntry.getValue().isValueNode()
                            ? nodeEntry.getValue() : formatAggregations(nodeEntry.getValue());

            outputAggregationNode.set(newName.get(), value);
        }

        return outputAggregationNode;
    }

    private static String extractQueryParamSearchTermOrDefault(RequestInfo requestInfo) {
        return requestInfo.getQueryParameters()
                   .getOrDefault(SEARCH_TERM_KEY, SEARCH_ALL_DOCUMENTS_DEFAULT_QUERY);
    }

    private static QueryStringQuery constructQueryStringQuery(RequestInfo requestInfo) {
        return new QueryStringQuery.Builder()
                   .query(extractQueryParamSearchTermOrDefault(requestInfo))
                   .build();
    }

    private Query contructQuery(RequestInfo requestInfo) {
        return new Query.Builder()
                   .queryString(constructQueryStringQuery(requestInfo))
                   .build();
    }
}

