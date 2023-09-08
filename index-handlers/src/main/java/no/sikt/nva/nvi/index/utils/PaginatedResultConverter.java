package no.sikt.nva.nvi.index.utils;

import static no.sikt.nva.nvi.index.SearchNviCandidatesHandler.QUERY_PARAM_SEARCH_TERM;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.index.model.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.commons.pagination.PaginatedSearchResult;
import nva.commons.apigateway.exceptions.UnprocessableContentException;
import nva.commons.core.Environment;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaginatedResultConverter {

    public static final Environment ENVIRONMENT = new Environment();
    private static final Logger LOGGER = LoggerFactory.getLogger(PaginatedResultConverter.class);
    private static final String WORD_ENDING_WITH_HASHTAG_REGEX = "[A-za-z0-9]*#";
    private static final Map<String, String> AGGREGATION_FIELDS_TO_CHANGE = Map.of(
        "doc_count_error_upper_bound", "docCountErrorUpperBound",
        "sum_other_doc_count", "sumOtherDocCount",
        "doc_count", "docCount");
    private static final String HOST = ENVIRONMENT.readEnv("API_HOST");
    private static final String CUSTOM_DOMAIN_BASE_PATH = ENVIRONMENT.readEnv("CUSTOM_DOMAIN_BASE_PATH");
    public static final String QUERY_PARAM_FILTER = "filter";

    private PaginatedResultConverter() {

    }

    public static PaginatedSearchResult<NviCandidateIndexDocument> toPaginatedResult(
        SearchResponse<NviCandidateIndexDocument> searchResponse, String searchTerm, String filter, int offset,
        int size)
        throws UnprocessableContentException {

        var paginatedSearchResult = PaginatedSearchResult.create(
            constructBaseUri(),
            offset,
            size,
            extractTotalNumberOfHits(searchResponse),
            extractsHits(searchResponse),
            Map.of(QUERY_PARAM_SEARCH_TERM, searchTerm, QUERY_PARAM_FILTER, filter),
            extractAggregations(searchResponse));

        LOGGER.info("Returning paginatedSearchResult with id: {}", paginatedSearchResult.getId().toString());
        return paginatedSearchResult;
    }

    private static int extractTotalNumberOfHits(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return (int) searchResponse.hits().total().value();
    }

    private static List<NviCandidateIndexDocument> extractsHits(
        SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream().map(Hit::source).toList();
    }

    private static URI constructBaseUri() {
        return UriWrapper.fromHost(HOST).addChild(CUSTOM_DOMAIN_BASE_PATH).getUri();
    }

    private static JsonNode extractAggregations(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();

        try (var generator = mapper.jsonProvider().createGenerator(writer)) {
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

            var newName = Optional.ofNullable(AGGREGATION_FIELDS_TO_CHANGE.get(fieldName));
            if (newName.isEmpty()) {
                newName = Optional.of(fieldName.replaceFirst(WORD_ENDING_WITH_HASHTAG_REGEX, ""));
            }

            var value = nodeEntry.getValue().isValueNode()
                            ? nodeEntry.getValue() : formatAggregations(nodeEntry.getValue());

            outputAggregationNode.set(newName.get(), value);
        }

        return outputAggregationNode;
    }
}
