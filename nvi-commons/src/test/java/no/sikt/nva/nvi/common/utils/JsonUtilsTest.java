package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.JsonUtils.extractOptJsonNodeTextValue;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonUtilsTest {

  private static final String FIELD_NAME = "fieldName";
  private static final String JSON_PATH_DELIMITER = "/";
  private static final String VALUE_NODE = "/value";

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

  @Test
  void shouldReturnFirstTextualValueOfAnArrayNodeWhenInputNodeIsArrayNodeWithValue()
      throws JsonProcessingException {
    var json =
        """
        {
          "value": [ "abc" ]
        }
        """;

    var textValue = JsonUtils.extractJsonNodeTextValue(objectMapper.readTree(json), VALUE_NODE);

    assertNotNull(textValue);
  }

  @Test
  void shouldReturnNullWhenInputNodeIsTextNodeWithNullValue() throws JsonProcessingException {
    var json =
        """
        {
          "value": null
        }
        """;

    var textValue = JsonUtils.extractJsonNodeTextValue(objectMapper.readTree(json), VALUE_NODE);

    assertNull(textValue);
  }

  @Test
  void shouldReturnNullWhenInputNodeIsArrayNodeWithNullValue() throws JsonProcessingException {
    var json =
        """
        {
          "value": [ null ]
        }
        """;

    var textValue = JsonUtils.extractJsonNodeTextValue(objectMapper.readTree(json), VALUE_NODE);

    assertNull(textValue);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {
          "value": 123
        }
        """,
        """
        {
          "value": [ 123 ]
        }
        """
      })
  void shouldThrowExceptionWhenInputNodeIsNonTextualOrNonArrayOfTextual(String json) {
    assertThrows(
        IllegalArgumentException.class,
        () -> JsonUtils.extractJsonNodeTextValue(objectMapper.readTree(json), VALUE_NODE));
  }
}
