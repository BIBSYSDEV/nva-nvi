package no.sikt.nva.nvi.common.dto;

import static org.junit.Assert.assertThrows;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class PublicationChannelDtoTest {

  @Test
  void shouldThrowIllegalArgumentExceptionWhenParsingUnknownValue() {
    var unknownValue = "UnknownValue";
    assertThrows(NoSuchElementException.class, () -> ScientificValue.parse(unknownValue));
  }
}
