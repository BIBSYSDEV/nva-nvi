package no.sikt.nva.nvi.common.service.dto.problem;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateProblemTest {

  @ParameterizedTest
  @MethodSource("provideCandidateProblemInstances")
  void shouldHandleRoundTripConversionOfImplementations(CandidateProblem candidateProblem)
      throws JsonProcessingException {
    var json = dtoObjectMapper.writeValueAsString(candidateProblem);
    var roundTripped = dtoObjectMapper.readValue(json, CandidateProblem.class);
    assertEquals(candidateProblem, roundTripped);
    assertEquals(candidateProblem.title(), roundTripped.title());
    assertEquals(candidateProblem.scope(), roundTripped.scope());
    assertEquals(candidateProblem.detail(), roundTripped.detail());
  }

  private static Stream<Arguments> provideCandidateProblemInstances() {
    return Stream.of(
        argumentSet("UnverifiedCreatorProblem", new UnverifiedCreatorProblem()),
        argumentSet(
            "UnverifiedCreatorFromOrganizationProblem",
            new UnverifiedCreatorFromOrganizationProblem(List.of("Bob"))));
  }
}
