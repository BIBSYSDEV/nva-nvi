package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringWriter;
import java.util.Map;
import java.util.Optional;
import no.sikt.nva.nvi.index.model.document.NviCandidateIndexDocument;
import no.unit.nva.commons.json.JsonUtils;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch.core.SearchResponse;

public final class AggregationFormatter {

    private static final String WORD_ENDING_WITH_HASHTAG_REGEX = "[A-za-z0-9]*#";
    private static final Map<String, String> AGGREGATION_FIELDS_TO_CHANGE = Map.of(
        "doc_count_error_upper_bound", "docCountErrorUpperBound",
        "sum_other_doc_count", "sumOtherDocCount",
        "doc_count", "docCount");

    private AggregationFormatter() {
    }

    public static JsonNode format(SearchResponse<NviCandidateIndexDocument> searchResponse) {
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
