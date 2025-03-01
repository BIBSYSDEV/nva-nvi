package no.sikt.nva.nvi.common.db;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.core.attempt.Try.attempt;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.document.EnhancedDocument;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@JsonSubTypes({@JsonSubTypes.Type(Dao.class)})
@JsonSerialize
public interface DynamoEntryWithRangeKey extends Typed {

  String FIELD_DELIMITER = "#";

  static Dao parseAttributeValuesMap(Map<String, AttributeValue> value, Class<Dao> daoClass) {
    return attempt(
            () ->
                dtoObjectMapper.readValue(
                    EnhancedDocument.fromAttributeValueMap(value).toJson(), daoClass))
        .orElseThrow();
  }

  String primaryKeyHashKey();

  String primaryKeyRangeKey();

  String version();
}
