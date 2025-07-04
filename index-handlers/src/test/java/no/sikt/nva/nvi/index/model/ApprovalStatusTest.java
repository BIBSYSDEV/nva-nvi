package no.sikt.nva.nvi.index.model;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;

import no.sikt.nva.nvi.index.model.document.ApprovalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApprovalStatusTest {

  @ParameterizedTest(name = "shouldParseValidStrings {0}")
  @ValueSource(strings = {"new", "PENDING", "Approved", "rejected"})
  void shouldParseValidStrings(String value) {
    assertThatNoException().isThrownBy(() -> ApprovalStatus.parse(value));
  }

  @Test
  void shouldThrowOnInvalidStrings() {
    assertThrows(IllegalArgumentException.class, () -> ApprovalStatus.parse("unknownValue"));
  }
}
