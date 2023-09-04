package no.sikt.nva.nvi.common.model.business;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record InstitutionPoints(URI institutionId, BigDecimal points) {

    public static final String INSTITUTION_ID_FIELD = "institutionId";
    public static final String POINTS_FIELD = "points";

    public static InstitutionPoints fromDynamoDb(AttributeValue input) {
        var map = input.m();
        return new InstitutionPoints(
            Optional.ofNullable(map.get(INSTITUTION_ID_FIELD))
                .map(attributeValue -> URI.create(attributeValue.s()))
                .orElse(null),
            Optional.ofNullable(map.get(POINTS_FIELD))
                .map(attributeValue -> new BigDecimal(attributeValue.n()))
                .orElse(null)
        );
    }

    public AttributeValue toDynamoDb(){
        var map = new HashMap<String, AttributeValue>();
        map.put(INSTITUTION_ID_FIELD, AttributeValue.fromS(institutionId.toString()));
        map.put(POINTS_FIELD, AttributeValue.fromN(points.toString()));
        return AttributeValue.fromM(map);
    }
}
