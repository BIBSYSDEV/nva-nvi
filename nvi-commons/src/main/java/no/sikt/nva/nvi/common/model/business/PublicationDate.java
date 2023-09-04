package no.sikt.nva.nvi.common.model.business;

import static java.util.Objects.nonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record PublicationDate(String year, String month, String day) {

    public static final String YEAR_FIELD = "year";
    public static final String MONTH_FIELD = "month";
    public static final String DAY_FIELD = "day";

    public AttributeValue toDynamoDb() {
        Map<String, AttributeValue> map = new HashMap<>();
        if (nonNull(year)) {
            map.put(YEAR_FIELD, AttributeValue.fromS(year));
        }
        if (nonNull(month)) {
            map.put(MONTH_FIELD, AttributeValue.fromS(month));
        }
        if (nonNull(day)) {
            map.put(DAY_FIELD, AttributeValue.fromS(day));
        }
        return AttributeValue.fromM(map);
    }

    public static PublicationDate fromDynamoDb(AttributeValue input) {
        var map = input.m();
        return new PublicationDate(
            Optional.ofNullable(map.get(YEAR_FIELD)).map(AttributeValue::s).orElse(null),
            Optional.ofNullable(map.get(MONTH_FIELD)).map(AttributeValue::s).orElse(null),
            Optional.ofNullable(map.get(DAY_FIELD)).map(AttributeValue::s).orElse(null)
        );
    }
}
