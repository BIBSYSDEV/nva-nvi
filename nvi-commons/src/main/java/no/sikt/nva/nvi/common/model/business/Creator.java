package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record Creator(URI creatorId, List<URI> affiliations) {

    public static final String CREATOR_ID_FIELD = "creatorId";
    public static final String AFFILIATIONS_FIELD = "affiliations";

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of(CREATOR_ID_FIELD, AttributeValue.fromS(creatorId.toString()),
                   AFFILIATIONS_FIELD, AttributeValue.fromL(affiliations.stream()
                                                                .map(URI::toString).map(AttributeValue::fromS).toList())
            ));
    }

    public static Creator fromDynamoDb(AttributeValue input) {
        var map = input.m();
        return new Creator(
            URI.create(map.get(CREATOR_ID_FIELD).s()),
            map.get(AFFILIATIONS_FIELD).l().stream().map(AttributeValue::s).map(URI::create).toList()
        );
    }
}
