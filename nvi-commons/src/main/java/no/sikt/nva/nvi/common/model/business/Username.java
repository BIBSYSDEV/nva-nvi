package no.sikt.nva.nvi.common.model.business;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record Username(String value) {

    public static final String VALUE_FIELD = "value";

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(VALUE_FIELD, AttributeValue.fromS(value)));
    }

    public static Username fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Username(
            map.get(VALUE_FIELD).s()
        );
    }
}
