package no.sikt.nva.nvi.index.utils;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringWriter;
import java.util.Map;
import no.unit.nva.commons.json.JsonUtils;
import org.opensearch.client.json.jsonb.JsonbJsonpMapper;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;

public final class AggregationFormatter {

    public static final String DOC_COUNT = "docCount";

    private AggregationFormatter() {
    }

    public static JsonNode format(Map<String, Aggregate> aggregations) {
        var root = new ObjectNode(JsonUtils.dtoObjectMapper.getNodeFactory());
        aggregations.forEach((key, value) -> root.set(key, format(value)));
        return root;
    }

    private static JsonNode format(Aggregate aggregate) {
        var aggregateNode = new ObjectNode(JsonUtils.dtoObjectMapper.getNodeFactory());
        var aggregateVariant = aggregate._get();
        if (aggregateVariant instanceof NestedAggregate nestedAggregate) {
            addAggregations(aggregateNode, nestedAggregate.docCount(), nestedAggregate.aggregations());
        } else if (aggregateVariant instanceof StringTermsAggregate stringTermsAggregate) {
            addBucketNodes(aggregateNode, stringTermsAggregate);
        } else {
            return serialize(aggregate);
        }
        return aggregateNode;
    }

    private static void addBucketNodes(ObjectNode aggregateNode, StringTermsAggregate stringTermsAggregate) {
        stringTermsAggregate.buckets()
            .array()
            .forEach(bucket -> aggregateNode.set(bucket.key(), generateBucketNode(bucket)));
    }

    private static ObjectNode generateBucketNode(StringTermsBucket bucket) {
        var node = new ObjectNode(JsonUtils.dtoObjectMapper.getNodeFactory());
        addAggregations(node, bucket.docCount(), bucket.aggregations());
        return node;
    }

    private static void addAggregations(ObjectNode aggregateNode, long docCount, Map<String, Aggregate> aggregateMap) {
        aggregateNode.put(DOC_COUNT, docCount);
        aggregateMap.forEach((key, value) -> aggregateNode.set(key, format(value)));
    }

    private static JsonNode serialize(Aggregate aggregate) {
        var writer = new StringWriter();
        var mapper = new JsonbJsonpMapper();
        try (var generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(aggregate, generator);
        }
        var aggregateNode = attempt(() -> JsonUtils.dtoObjectMapper.readTree(writer.toString())).orElseThrow();
        return replaceDocCount(aggregateNode);
    }

    private static ObjectNode replaceDocCount(JsonNode jsonNode) {
        var object = (ObjectNode) jsonNode;
        if (jsonNode.has("doc_count")) {
            object.set(DOC_COUNT, jsonNode.get("doc_count"));
            object.remove("doc_count");
        }
        return object;
    }
}
