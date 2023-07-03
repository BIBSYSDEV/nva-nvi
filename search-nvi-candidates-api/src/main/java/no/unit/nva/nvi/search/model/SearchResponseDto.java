package no.unit.nva.nvi.search.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.attempt.Try;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;

public record SearchResponseDto(@JsonProperty("@context") URI context,
                                URI id,
                                long processingTime,
                                long size,
                                List<JsonNode> hits,
                                JsonNode aggregations) {

    public static final URI DEFAULT_SEARCH_CONTEXT = URI.create("https://api.nva.unit.no/resources/search");
    public static final String QUERY_PARAMETER = "query";
    public static final String WORD_ENDING_WITH_HASHTAG_REGEX = "[A-za-z0-9]*#";

    private static final Map<String, String> AGGREGATION_FIELDS_TO_CHANGE = Map.of(
        "doc_count_error_upper_bound", "docCountErrorUpperBound",
        "sum_other_doc_count", "sumOtherDocCount",
        "doc_count", "docCount");

    public static SearchResponseDto fromSearchResponse(SearchResponse searchResponse, URI id) {
        List<JsonNode> sourcesList = extractSourcesList(searchResponse);
        long total = searchResponse.getHits().getTotalHits().value;
        long took = searchResponse.getTook().duration();
        var aggregations = extractAggregations(searchResponse);

        return new Builder()
                   .withContext(DEFAULT_SEARCH_CONTEXT)
                   .withId(id)
                   .withHits(sourcesList)
                   .withSize(total)
                   .withProcessingTime(took)
                   .withAggregations(aggregations)
                   .build();
    }

    public static URI createIdWithQuery(URI requestUri, String searchTerm) {
        UriWrapper wrapper = UriWrapper.fromUri(requestUri);
        if (nonNull(searchTerm)) {
            wrapper = wrapper.addQueryParameter(QUERY_PARAMETER, searchTerm);
        }
        return wrapper.getUri();
    }

    private static List<JsonNode> extractSourcesList(SearchResponse searchResponse) {
        return Arrays.stream(searchResponse.getHits().getHits())
                   .map(SearchHit::getSourceAsMap)
                   .map(source -> JsonUtils.dtoObjectMapper.convertValue(source, JsonNode.class))
                   .collect(Collectors.toList());
    }

    private static JsonNode extractAggregations(SearchResponse searchResponse) {
        JsonNode json = Try.attempt(() -> JsonUtils.dtoObjectMapper.readTree(searchResponse.toString())).orElseThrow();

        ObjectNode aggregations = (ObjectNode) json.get("aggregations");

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

            var value = nodeEntry.getValue();
            if (value.isValueNode()) {
                outputAggregationNode.set(newName.get(), value);
            } else if (value.isArray()) {
                var arrayNode = JsonUtils.dtoObjectMapper.createArrayNode();
                value.forEach(element -> arrayNode.add(formatAggregations(element)));
                outputAggregationNode.set(newName.get(), arrayNode);
            } else {
                outputAggregationNode.set(newName.get(), formatAggregations(nodeEntry.getValue()));
            }
        }

        return outputAggregationNode;
    }

    public static class Builder {

        private URI context;
        private URI id;
        private long processingTime;
        private long size;
        private List<JsonNode> hits;
        private JsonNode aggregations;

        public Builder withContext(URI context) {
            this.context = context;
            return this;
        }

        public Builder withHits(List<JsonNode> hits) {
            this.hits = hits;
            return this;
        }

        public Builder withId(URI id) {
            this.id = id;
            return this;
        }

        public Builder withSize(long size) {
            this.size = size;
            return this;
        }

        public Builder withProcessingTime(long processingTime) {
            this.processingTime = processingTime;
            return this;
        }

        public Builder withAggregations(JsonNode aggregations) {
            this.aggregations = aggregations;
            return this;
        }

        public SearchResponseDto build() {

            return new SearchResponseDto(context, id, processingTime, size, hits, aggregations);
        }
    }
}
