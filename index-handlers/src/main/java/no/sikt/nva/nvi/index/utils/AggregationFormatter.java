package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringWriter;
import java.util.Map;
import no.unit.nva.commons.json.JsonUtils;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;

public final class AggregationFormatter {

    private AggregationFormatter() {
    }

    public static JsonNode format(Map<String, Aggregate> aggregations) {
        ObjectNode root = new ObjectNode(JsonUtils.dtoObjectMapper.getNodeFactory());

        aggregations.forEach((key, value) -> {
            root.set(key, formatAggregation(value));
        });

        return root;
    }

    private static JsonNode formatAggregation(Aggregate aggregate) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();
        try (var generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(aggregate, generator);
        }
        return attempt(() -> JsonUtils.dtoObjectMapper.readTree(writer.toString())).orElseThrow();
    }
}
