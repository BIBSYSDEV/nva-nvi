package no.sikt.nva.nvi.common.db.model;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;
import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// FIXME: Rewrite this
public class DbCreatorTypeListConverter implements AttributeConverter<List<DbCreatorType>> {

    @Override
    public AttributeValue transformFrom(List<DbCreatorType> creators) {
        return AttributeValue.builder()
                   .l(creators.stream().map(this::toAttributeValue).toList()).build();
    }

    @Override
    public List<DbCreatorType> transformTo(AttributeValue attributeValueList) {
        return attributeValueList.l().stream().map(attributeValue -> toCreator(attributeValue.m())).toList();
    }

    @Override
    public EnhancedType<List<DbCreatorType>> type() {
        return EnhancedType.listOf(DbCreatorType.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.L;
    }

    private DbCreatorType toCreator(Map<String, AttributeValue> attributeValueMap) {
        return attempt(
            () -> dynamoObjectMapper.readValue(EnhancedDocument.fromAttributeValueMap(attributeValueMap).toJson(),
                                               DbCreatorType.class))
                   .orElseThrow();
    }

    private AttributeValue toAttributeValue(DbCreatorType creator) {
        return AttributeValue.builder()
                   .m(EnhancedDocument.fromJson(
                           attempt(() -> dynamoObjectMapper.writeValueAsString(creator)).orElseThrow())
                          .toMap())
                   .build();
    }
}
