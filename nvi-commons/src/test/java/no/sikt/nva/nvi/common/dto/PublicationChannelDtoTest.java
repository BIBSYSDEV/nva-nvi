package no.sikt.nva.nvi.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import no.sikt.nva.nvi.common.model.ScientificValue;
import org.junit.jupiter.api.Test;

class PublicationChannelDtoTest {

  @Test
  void shouldParseUnknownValueAsInvalid() {
    var unknownValue = "UnknownValue";
    var parsedValue = ScientificValue.parse(unknownValue);
    assertThat(parsedValue).isEqualTo(ScientificValue.INVALID);
    assertThat(parsedValue.isValid()).isFalse();
  }
}
