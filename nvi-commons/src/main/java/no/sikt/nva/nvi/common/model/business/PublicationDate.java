package no.sikt.nva.nvi.common.model.business;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record PublicationDate(String year, String month, String day) {

    public static final String YEAR_FIELD = "year";
    public static final String MONTH_FIELD = "month";
    public static final String DAY_FIELD = "day";

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(YEAR_FIELD, AttributeValue.fromS(year),
                   MONTH_FIELD, AttributeValue.fromS(month),
                   DAY_FIELD, AttributeValue.fromS(day)
            ));
    }

    public static PublicationDate fromDynamoDb(AttributeValue input) {
        var map = input.m();
        return new PublicationDate(
            map.get(YEAR_FIELD).s(),
            map.get(MONTH_FIELD).s(),
            map.get(DAY_FIELD).s()
        );
    }
}
