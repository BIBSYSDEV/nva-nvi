package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.unit.nva.commons.json.JsonUtils;
import nva.commons.core.JacocoGenerated;
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

    public static final URI DEFAULT_SEARCH_CONTEXT = URI.create("https://api.nva.unit.no/nvi-candidates/search");

    public static SearchResponseDto fromSearchResponse(SearchResponse<NviCandidateIndexDocument> searchResponse) {
        List<JsonNode> sourcesList = extractSourcesList(searchResponse);
        long total = searchResponse.hits().total().value();
        long took = searchResponse.took();

        return new Builder()
                   .withContext(DEFAULT_SEARCH_CONTEXT)
                   .withHits(sourcesList)
                   .withSize(total)
                   .withProcessingTime(took)
                   .build();
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
