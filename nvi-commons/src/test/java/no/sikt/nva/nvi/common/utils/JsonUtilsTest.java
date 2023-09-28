package no.sikt.nva.nvi.common.utils;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    @Test
    void shouldExtractJsonNodeTextValueIfPresent() {
        var fieldName = "fieldName";
        var fieldValue = randomString();
        var randomJsonNode = objectMapper.createObjectNode().put(fieldName, fieldValue);
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + fieldName), is(equalTo(fieldValue)));
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToMissingNode() {
        var fieldName = "fieldName";
        var randomJsonNode = objectMapper.createObjectNode();
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + fieldName), nullValue());
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToNull() {
        var fieldName = "fieldName";
        var randomJsonNode = objectMapper.createObjectNode().set(fieldName, null);
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + fieldName), nullValue());
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToJsonNode() {
        var fieldName = "fieldName";
        var randomJsonNode = objectMapper.createObjectNode().set(fieldName, objectMapper.createObjectNode());
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + fieldName), nullValue());
    }

    @Test
    void shouldReturnStreamNode() {
        var randomJsonNode = objectMapper.createObjectNode();
        assertThat(JsonUtils.streamNode(randomJsonNode).toList(),
                   is(equalTo(StreamSupport.stream(randomJsonNode.spliterator(), false).toList())));
    }
}