package no.sikt.nva.nvi.common.db.model;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import java.util.List;
import java.util.Map;
import no.sikt.nva.nvi.common.db.CandidateDao.DbCreatorType;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Custom AttributeConverter for converting a list of {@link DbCreatorType} to and from a format we
 * can store in DynamoDB.
 */
public class DbCreatorTypeListConverter implements AttributeConverter<List<DbCreatorType>> {

  @Override
  public AttributeValue transformFrom(List<DbCreatorType> creators) {
    return AttributeValue.builder()
        .l(creators.stream().map(this::toAttributeValue).toList())
        .build();
  }

  @Override
  public List<DbCreatorType> transformTo(AttributeValue attributeValueList) {
    return attributeValueList.l().stream()
        .map(attributeValue -> toCreator(attributeValue.m()))
        .toList();
  }

  // This isn't used, but it's required by the interface
  @Override
  @JacocoGenerated
  public EnhancedType<List<DbCreatorType>> type() {
    return EnhancedType.listOf(DbCreatorType.class);
  }

  @Override
  public AttributeValueType attributeValueType() {
    return AttributeValueType.L;
  }

  private DbCreatorType toCreator(Map<String, AttributeValue> attributeValueMap) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(
                    EnhancedDocument.fromAttributeValueMap(attributeValueMap).toJson(),
                    DbCreatorType.class))
        .orElseThrow();
  }

  private AttributeValue toAttributeValue(DbCreatorType creator) {
    return AttributeValue.builder()
        .m(
            EnhancedDocument.fromJson(
                    attempt(() -> dtoObjectMapper.writeValueAsString(creator)).orElseThrow())
                .toMap())
        .build();
  }
}
