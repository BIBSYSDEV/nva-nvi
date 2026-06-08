package no.sikt.nva.nvi.rdf;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JsonLdFrameTest {

  @Test
  void shouldThrowRdfProcessingExceptionWhenFrameResourceIsNotValidJson() {
    assertThrows(
        RdfProcessingException.class, () -> JsonLdFrame.fromResource("invalid-frame.json"));
  }
}
