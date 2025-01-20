package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dynamoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.db.model.KeyField;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonSerialize
@JsonSubTypes({
  @JsonSubTypes.Type(value = ApprovalStatusDao.class, name = ApprovalStatusDao.TYPE),
  @JsonSubTypes.Type(value = CandidateDao.class, name = CandidateDao.TYPE),
  @JsonSubTypes.Type(value = NoteDao.class, name = NoteDao.TYPE),
  @JsonSubTypes.Type(value = NviPeriodDao.class, name = NviPeriodDao.TYPE),
  @JsonSubTypes.Type(
      value = CandidateUniquenessEntryDao.class,
      name = CandidateUniquenessEntryDao.TYPE)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public abstract class Dao implements DynamoEntryWithRangeKey {

  public static final String KEY_FIELD_DELIMITER = "#";

  public static String scanFilterExpressionForDataEntries(Collection<KeyField> types) {
    return getTypesOrDefault(types).map(Dao::toQueryPart).collect(Collectors.joining(" or \n"));
  }

  public static Map<String, AttributeValue> scanFilterExpressionAttributeValues(
      Collection<KeyField> types) {
    return getTypesOrDefault(types)
        .map(Dao::createFilterExpression)
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  @DynamoDbIgnore
  public Map<String, AttributeValue> toDynamoFormat() {
    return EnhancedDocument.fromJson(
            attempt(() -> dynamoObjectMapper.writeValueAsString(this)).orElseThrow())
        .toMap();
  }

  private static Entry<String, AttributeValue> createFilterExpression(KeyField keyField) {
    return switch (keyField) {
      case CANDIDATE ->
          Map.entry(
              keyField.getKeyField(),
              AttributeValue.fromS(CandidateDao.TYPE + KEY_FIELD_DELIMITER));
      case APPROVAL ->
          Map.entry(
              keyField.getKeyField(),
              AttributeValue.fromS(ApprovalStatusDao.TYPE + KEY_FIELD_DELIMITER));
      case NOTE ->
          Map.entry(
              keyField.getKeyField(), AttributeValue.fromS(NoteDao.TYPE + KEY_FIELD_DELIMITER));
      case PERIOD ->
          Map.entry(
              keyField.getKeyField(),
              AttributeValue.fromS(NviPeriodDao.TYPE + KEY_FIELD_DELIMITER));
    };
  }

  private static Stream<KeyField> getTypesOrDefault(Collection<KeyField> types) {
    return types.isEmpty() ? Stream.of(KeyField.values()) : types.stream();
  }

  private static String toQueryPart(KeyField type) {
    return "begins_with (#PK, " + type.getKeyField() + ")";
  }
}
