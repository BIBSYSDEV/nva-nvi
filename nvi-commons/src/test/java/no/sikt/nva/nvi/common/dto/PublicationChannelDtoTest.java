package no.sikt.nva.nvi.common.dto;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.NoSuchElementException;
import no.sikt.nva.nvi.common.model.ScientificValue;
import org.junit.jupiter.api.Test;

class PublicationChannelDtoTest {

  @Test
  void shouldThrowIllegalArgumentExceptionWhenParsingUnknownValue() {
    var unknownValue = "UnknownValue";
    assertThrows(NoSuchElementException.class, () -> ScientificValue.parse(unknownValue));
  }
}
