package no.sikt.nva.nvi.common.utils;

import static no.sikt.nva.nvi.common.utils.JsonUtils.extractOptJsonNodeTextValue;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

    public static final String ID_FIELD = "id";

    @Test
    void shouldExtractJsonNodeTextValueIfPresent() {
        var fieldName = "fieldName";
        var fieldValue = randomString();
        var randomJsonNode = objectMapper.createObjectNode().put(fieldName, fieldValue);
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + fieldName), is(equalTo(fieldValue)));
    }

    @Test
    void shouldExtractOptionalJsonNodeTextValueIfPresent() {
        var fieldName = "fieldName";
        var fieldValue = randomString();
        var randomJsonNode = objectMapper.createObjectNode().put(fieldName, fieldValue);
        assertThat(extractOptJsonNodeTextValue(randomJsonNode, "/" + fieldName).get(), is(equalTo(fieldValue)));
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

    @Test
    void shouldExtractId() {
        var id = randomUri();
        var jsonNode = objectMapper.createObjectNode().put(ID_FIELD, id.toString());
        assertThat(JsonUtils.extractId(jsonNode), is(equalTo(id)));
    }
}