package no.sikt.nva.nvi.common.dto;

import static org.junit.Assert.assertThrows;

import org.junit.jupiter.api.Test;

class PublicationChannelDtoTest {

  @Test
  void shouldThrowIllegalArgumentExceptionWhenParsingUnknownValue() {
    var unknownValue = "UnknownValue";
    assertThrows(IllegalArgumentException.class, () -> ScientificValue.valueOf(unknownValue));
  }
}
