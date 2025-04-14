package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.JsonUtils.extractOptJsonNodeTextValue;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  public static final String ID_FIELD = "id";
  private static final String FIELD_NAME = "fieldName";
  private static final String JSON_PATH_DELIMITER = "/";

  @Test
  void shouldExtractJsonNodeTextValueIfPresent() {
    var fieldValue = randomString();
    var randomJsonNode = objectMapper.createObjectNode().put(FIELD_NAME, fieldValue);
    assertEquals(
        JsonUtils.extractJsonNodeTextValue(randomJsonNode, JSON_PATH_DELIMITER + FIELD_NAME),
        fieldValue);
  }

  @Test
  void shouldExtractOptionalJsonNodeTextValueIfPresent() {
    var fieldValue = randomString();
    var randomJsonNode = objectMapper.createObjectNode().put(FIELD_NAME, fieldValue);
    assertEquals(
        extractOptJsonNodeTextValue(randomJsonNode, JSON_PATH_DELIMITER + FIELD_NAME).get(),
        fieldValue);
  }

  @Test
  void shouldReturnNullIfJsonPointerPointsToMissingNode() {
    var randomJsonNode = objectMapper.createObjectNode();
    assertNull(
        JsonUtils.extractJsonNodeTextValue(randomJsonNode, JSON_PATH_DELIMITER + FIELD_NAME));
  }

  @Test
  void shouldReturnNullIfJsonPointerPointsToNull() {
    var randomJsonNode = objectMapper.createObjectNode().set(FIELD_NAME, null);
    assertNull(
        JsonUtils.extractJsonNodeTextValue(randomJsonNode, JSON_PATH_DELIMITER + FIELD_NAME));
  }

  @Test
  void shouldReturnNullIfJsonPointerPointsToJsonNode() {
    var randomJsonNode =
        objectMapper.createObjectNode().set(FIELD_NAME, objectMapper.createObjectNode());
    assertNull(
        JsonUtils.extractJsonNodeTextValue(randomJsonNode, JSON_PATH_DELIMITER + FIELD_NAME));
  }

  @Test
  void shouldReturnStreamNode() {
    var randomJsonNode = objectMapper.createObjectNode();
    assertEquals(
        JsonUtils.streamNode(randomJsonNode).toList(),
        StreamSupport.stream(randomJsonNode.spliterator(), false).toList());
  }
}
