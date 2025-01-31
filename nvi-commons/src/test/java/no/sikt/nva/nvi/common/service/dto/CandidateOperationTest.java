package no.sikt.nva.nvi.common.service.dto;

import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;
import static nva.commons.core.attempt.Try.attempt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateOperationTest {

  @ParameterizedTest(name = "should serialize {0} to {1}")
  @MethodSource("candidateOperationSerializationProvider")
  void shouldSerializeUsingValue(CandidateOperation candidateOperation, String expectedValue) {
    var serialized =
        attempt(() -> objectMapper.writeValueAsString(candidateOperation)).orElseThrow();
    assertEquals(expectedValue, serialized);
  }

  private static Stream<Arguments> candidateOperationSerializationProvider() {
    return Stream.of(
        argumentSet(
            "APPROVAL_REJECT", CandidateOperation.APPROVAL_REJECT, "\"approval/reject-candidate\""),
        argumentSet(
            "APPROVAL_APPROVE",
            CandidateOperation.APPROVAL_APPROVE,
            "\"approval/approve-candidate\""),
        argumentSet(
            "APPROVAL_PENDING",
            CandidateOperation.APPROVAL_PENDING,
            "\"approval/reset-approval\""));
  }
}
