package no.sikt.nva.nvi.index.model;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.json.stream.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record SearchResponseDto(@JsonProperty("@context") URI context,
                                URI id,
                                long processingTime,
                                long size,
                                List<JsonNode> hits,
                                JsonNode aggregations) {

    public static final String NVI_CANDIDATES = "nvi-candidates";
    public static final String SEARCH = "search";
    public static final String API_HOST = "API_HOST";
    public static final String WORD_ENDING_WITH_HASHTAG_REGEX = "[A-za-z0-9]*#";
    private static final Map<String, String> AGGREGATION_FIELDS_TO_CHANGE = Map.of(
        "doc_count_error_upper_bound", "docCountErrorUpperBound",
        "sum_other_doc_count", "sumOtherDocCount",
        "doc_count", "docCount");

    public static SearchResponseDto fromSearchResponse(SearchResponse<NviCandidateIndexDocument> searchResponse)
        throws IOException {
        List<JsonNode> sourcesList = extractSourcesList(searchResponse);
        long total = searchResponse.hits().total().value();
        long took = searchResponse.took();
        return new Builder()
                   .withContext(constructContextUri())
                   .withHits(sourcesList)
                   .withSize(total)
                   .withProcessingTime(took)
                   .withAggregations(extractAggregations(searchResponse))
                   .build();
    }

    private static JsonNode extractAggregations(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();

        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(searchResponse, generator);
        }

        JsonNode json = attempt(() -> JsonUtils.dtoObjectMapper.readTree(writer.toString())).orElseThrow();
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
            } else {
                outputAggregationNode.set(newName.get(), formatAggregations(nodeEntry.getValue()));
            }
        }

        return outputAggregationNode;
    }

    private static URI constructContextUri() {
        return UriWrapper.fromHost(new Environment().readEnv(API_HOST))
                   .addChild(NVI_CANDIDATES)
                   .addChild(SEARCH)
                   .getUri();
    }

    private static List<JsonNode> extractSourcesList(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        return searchResponse.hits().hits().stream()
                   .map(Hit::source)
                   .map(source -> JsonUtils.dtoObjectMapper.convertValue(source, JsonNode.class))
                   .toList();
    }

    @JacocoGenerated
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
