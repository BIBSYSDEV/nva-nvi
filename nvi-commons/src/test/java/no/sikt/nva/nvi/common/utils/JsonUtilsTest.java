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
    private static final String FIELD_NAME = "fieldName";

    @Test
    void shouldExtractJsonNodeTextValueIfPresent() {
        var fieldValue = randomString();
        var randomJsonNode = objectMapper.createObjectNode().put(FIELD_NAME, fieldValue);
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + FIELD_NAME), is(equalTo(fieldValue)));
    }

    @Test
    void shouldExtractOptionalJsonNodeTextValueIfPresent() {
        var fieldValue = randomString();
        var randomJsonNode = objectMapper.createObjectNode().put(FIELD_NAME, fieldValue);
        assertThat(extractOptJsonNodeTextValue(randomJsonNode, "/" + FIELD_NAME).get(), is(equalTo(fieldValue)));
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToMissingNode() {
        var randomJsonNode = objectMapper.createObjectNode();
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + FIELD_NAME), nullValue());
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToNull() {
        var randomJsonNode = objectMapper.createObjectNode().set(FIELD_NAME, null);
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + FIELD_NAME), nullValue());
    }

    @Test
    void shouldReturnNullIfJsonPointerPointsToJsonNode() {
        var randomJsonNode = objectMapper.createObjectNode().set(FIELD_NAME, objectMapper.createObjectNode());
        assertThat(JsonUtils.extractJsonNodeTextValue(randomJsonNode, "/" + FIELD_NAME), nullValue());
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