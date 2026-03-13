package no.sikt.nva.nvi.common.dto;

import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_1_CONTRIBUTOR;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_1;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_2;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_3;
import static no.sikt.nva.nvi.common.examples.ExampleContributors.EXAMPLE_2_CONTRIBUTOR_5;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_CREATOR;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.ROLE_OTHER;
import static no.sikt.nva.nvi.common.model.ContributorFixtures.STATUS_VERIFIED;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
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
    assertTrue(contributor.isNamed());
  }

  @ParameterizedTest
  @MethodSource("exampleContributorProvider")
  void shouldHandleRoundTripConversion(ContributorDto contributor) throws JsonProcessingException {
    var json = dtoObjectMapper.writeValueAsString(contributor);
    var roundTripped = dtoObjectMapper.readValue(json, ContributorDto.class);
    assertEquals(contributor, roundTripped);
  }

  @Test
  void shouldPreserveOrcidWhenBuildingContributorDto() {
    var expectedOrcid = "0000-0001-2345-6789";
    var contributor =
        ContributorDto.builder()
            .withName("Test Person")
            .withOrcid(expectedOrcid)
            .withRole(ROLE_CREATOR)
            .withVerificationStatus(STATUS_VERIFIED)
            .build();
    assertEquals(expectedOrcid, contributor.orcid());
  }

  @Test
  void shouldPreserveMultipleRolesWhenBuildingContributorDto() {
    var expectedRoles = List.of(ROLE_CREATOR, ROLE_OTHER);
    var contributor =
        ContributorDto.builder()
            .withName("Test Person")
            .withRole(expectedRoles)
            .withVerificationStatus(STATUS_VERIFIED)
            .build();
    assertEquals(expectedRoles, contributor.roles());
  }

  private static Stream<Arguments> exampleContributorProvider() {
    return Stream.of(
        argumentSet("EXAMPLE_1_CONTRIBUTOR", EXAMPLE_1_CONTRIBUTOR),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_1", EXAMPLE_2_CONTRIBUTOR_1),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_2", EXAMPLE_2_CONTRIBUTOR_2),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_3", EXAMPLE_2_CONTRIBUTOR_3),
        argumentSet("EXAMPLE_2_CONTRIBUTOR_5", EXAMPLE_2_CONTRIBUTOR_5));
  }
}
