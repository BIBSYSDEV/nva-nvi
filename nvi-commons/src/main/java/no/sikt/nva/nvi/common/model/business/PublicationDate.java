package no.sikt.nva.nvi.common.model.business;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record PublicationDate(String year, String month, String day) {

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of("year", AttributeValue.fromS(year),
                   "month", AttributeValue.fromS(month),
                   "day", AttributeValue.fromS(day)
            ));
    }

    public static PublicationDate fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new PublicationDate(
            map.get("year").s(),
            map.get("month").s(),
            map.get("day").s()
        );
    }
}
