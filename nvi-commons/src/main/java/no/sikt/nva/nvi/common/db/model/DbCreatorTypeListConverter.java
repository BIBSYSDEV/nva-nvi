package no.sikt.nva.nvi.common.db.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// FIXME: Rewrite this
public class DbCreatorTypeListConverter implements AttributeConverter<List<DbCreatorType>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public AttributeValue transformFrom(List<DbCreatorType> creators) {
        try {
            return AttributeValue.builder()
                                 .s(MAPPER.writeValueAsString(creators))
                                 .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize creators", e);
        }
    }

    @Override
    public List<DbCreatorType> transformTo(AttributeValue attributeValue) {
        try {
            String json = attributeValue.s();
            JsonNode root = MAPPER.readTree(json);

            // FIXME: Hardcoding an assumption to handle existing data with no type field
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (!node.has("type")) {
                        ((ObjectNode) node).put("type", "DbCreator");
                    }
                }
            }
            return MAPPER.readValue(json, new TypeReference<List<DbCreatorType>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize creators", e);
        }
    }

    @Override
    public EnhancedType<List<DbCreatorType>> type() {
        return EnhancedType.listOf(DbCreatorType.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
