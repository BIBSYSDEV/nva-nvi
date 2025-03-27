package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_1_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_2;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_3;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_4;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_5;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContributorDtoTest {

  @Test
  void shouldFindExpectedValuesInExampleData() {
    var contributor = EXAMPLE_1_CONTRIBUTOR;
    assertTrue(contributor.isCreator());
    assertTrue(contributor.isVerified());
  }

  @ParameterizedTest
  @MethodSource("exampleContributorProvider")
  void shouldHandleRoundTripConversion(ContributorDto contributor) throws JsonProcessingException {
    var json = dtoObjectMapper.writeValueAsString(contributor);
    var roundTripped = dtoObjectMapper.readValue(json, ContributorDto.class);
    assertEquals(contributor, roundTripped);
  }

  private static Stream<Arguments> exampleContributorProvider() {
    return Stream.of(
        argumentSet("EXAMPLE_1_CONTRIBUTOR", EXAMPLE_1_CONTRIBUTOR),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_1", EXAMPLE_2_CONTRIBUTOR_1),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_2", EXAMPLE_2_CONTRIBUTOR_2),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_3", EXAMPLE_2_CONTRIBUTOR_3),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_4", EXAMPLE_2_CONTRIBUTOR_4),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_5", EXAMPLE_2_CONTRIBUTOR_5));
  }
}
