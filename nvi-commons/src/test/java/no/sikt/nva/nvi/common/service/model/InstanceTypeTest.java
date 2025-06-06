package no.sikt.nva.nvi.common.service.model;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import no.sikt.nva.nvi.common.model.InstanceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InstanceTypeTest {

  @ParameterizedTest(name = "shouldParseValidStrings {0}")
  @ValueSource(
      strings = {
        "AcademicCommentary",
        "AcademicMonograph",
        "AcademicChapter",
        "AcademicArticle",
        "AcademicLiteratureReview"
      })
  void shouldParseValidStrings(String value) {
    var instanceType = InstanceType.parse(value);
    assertEquals(value, instanceType.getValue());
  }

  @Test
  void shouldSerializeAndDeserializeUsingValue() {
    var instanceType = InstanceType.ACADEMIC_ARTICLE;
    var serialized = attempt(() -> objectMapper.writeValueAsString(instanceType)).orElseThrow();
    var deserialized =
        attempt(() -> objectMapper.readValue(serialized, InstanceType.class)).orElseThrow();
    assertEquals(instanceType, deserialized);
  }

  @Test
  void shouldParseUnknownValueAsInvalid() {
    var unknownValue = "UnknownValue";
    var parsedValue = InstanceType.parse(unknownValue);
    assertThat(parsedValue).isEqualTo(InstanceType.NON_CANDIDATE);
    assertThat(parsedValue.isValid()).isFalse();
  }
}
