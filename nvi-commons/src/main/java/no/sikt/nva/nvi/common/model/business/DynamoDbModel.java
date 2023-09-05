package no.sikt.nva.nvi.common.model.business;

import static nva.commons.core.attempt.Try.attempt;
import no.unit.nva.commons.json.JsonUtils;
import software.amazon.awssdk.enhanced.dynamodb.internal.converter.attribute.JsonItemAttributeConverter;
import software.amazon.awssdk.protocols.jsoncore.internal.StringJsonNode;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public interface DynamoDbModel<T> {

    default T fromDynamoDb(AttributeValue input, Class<T> clazz) {
        return attempt(() -> JsonUtils.dtoObjectMapper.readValue(input.s(), clazz)).orElseThrow();
    }

    default AttributeValue toDynamoDb() {
        var string = attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(this)).orElseThrow();
        var jsonNode = JsonUtils.dtoObjectMapper.convertValue(string, StringJsonNode.class);
        return attempt(() -> JsonItemAttributeConverter.create().transformFrom(jsonNode)).orElseThrow();
    }
}
