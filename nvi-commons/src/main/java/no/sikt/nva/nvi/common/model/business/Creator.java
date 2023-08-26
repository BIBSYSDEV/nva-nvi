package no.sikt.nva.nvi.common.model.business;

import java.net.URI;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public record Creator(URI creatorId, List<URI> affiliations) {

    public AttributeValue toDynamoDb() {
        return AttributeValue.fromM(
            Map.of("creatorId", AttributeValue.fromS(creatorId.toString()),
                   "affiliations", AttributeValue.fromL(affiliations.stream().map(URI::toString).map(AttributeValue::fromS).toList())
            ));
    }

    public static Creator fromDynamoDb(AttributeValue input) {
        Map<String, AttributeValue> map = input.m();
        return new Creator(
            URI.create(map.get("creatorId").s()),
            map.get("affiliations").l().stream().map(AttributeValue::s).map(URI::create).toList()
        );
    }
}
