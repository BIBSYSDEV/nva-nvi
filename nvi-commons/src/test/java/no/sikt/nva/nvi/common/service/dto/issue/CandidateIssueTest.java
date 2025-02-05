package no.sikt.nva.nvi.common.service.dto.issue;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CandidateIssueTest {

  @ParameterizedTest
  @MethodSource("provideCandidateIssueInstances")
  void shouldHandleRoundTripConversionOfImplementations(CandidateIssue candidateIssue)
      throws JsonProcessingException {
    var json = dtoObjectMapper.writeValueAsString(candidateIssue);
    var roundTrippedIssue = dtoObjectMapper.readValue(json, CandidateIssue.class);
    assertEquals(candidateIssue, roundTrippedIssue);
    assertEquals(candidateIssue.title(), roundTrippedIssue.title());
    assertEquals(candidateIssue.scope(), roundTrippedIssue.scope());
    assertEquals(candidateIssue.description(), roundTrippedIssue.description());
  }

  private static Stream<Arguments> provideCandidateIssueInstances() {
    return Stream.of(argumentSet("default UnverifiedCreatorIssue", new UnverifiedCreatorIssue()));
  }
}
