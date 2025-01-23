package no.sikt.nva.nvi.events.evaluator.dto;

import static java.util.Collections.emptyList;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ContributorDtoTest {

  private static final String CONTRIBUTOR_WITH_EMPTY_AFFILIATIONS =
      """
      {
          "type": "Contributor",
          "identity": {
              "type": "Identity",
              "name": "I.N. Kognito",
              "verificationStatus": "NotVerified",
              "additionalIdentifiers": []
          },
          "affiliations": [],
          "role": {
              "type": "Creator"
          },
          "sequence": 1,
          "correspondingAuthor": false
      }
      """;

  private static final String CONTRIBUTOR_WITH_MISSING_AFFILIATIONS =
      """
      {
          "type": "Contributor",
          "identity": {
              "type": "Identity",
              "name": "I.N. Kognito",
              "verificationStatus": "NotVerified",
              "additionalIdentifiers": []
          },
          "role": {
              "type": "Creator"
          },
          "sequence": 1,
          "correspondingAuthor": false
      }
      """;

  private static final String CONTRIBUTOR_WITH_MULTIPLE_NAMES =
      """
      {
          "type": "Contributor",
          "identity": {
              "type": "Identity",
              "name": ["I.N. Kognito", "Ignacio N. Kognito"],
              "verificationStatus": "NotVerified",
              "additionalIdentifiers": []
          },
          "role": {
              "type": "Creator"
          },
          "sequence": 1,
          "correspondingAuthor": false
      }
      """;

  private static Stream<Arguments> validContributorJsonProvider() {
    return Stream.of(
        Arguments.of(Named.of("WithEmptyListOfAffiliations", CONTRIBUTOR_WITH_EMPTY_AFFILIATIONS)),
        Arguments.of(
            Named.of("WithMissingListOfAffiliations", CONTRIBUTOR_WITH_MISSING_AFFILIATIONS)),
        Arguments.of(Named.of("WithArrayOfNames", CONTRIBUTOR_WITH_MULTIPLE_NAMES)));
  }

  @ParameterizedTest
  @MethodSource("validContributorJsonProvider")
  void fromJsonNode(String jsonContributor) throws JsonProcessingException {
    var contributorNode = parseJsonString(jsonContributor);
    var expectedContributor =
        new ContributorDto("I.N. Kognito", null, "NotVerified", "Creator", emptyList());
    var actualContributor = ContributorDto.fromJsonNode(contributorNode);
    assertEquals(expectedContributor, actualContributor);
  }

  private JsonNode parseJsonString(String content) throws JsonProcessingException {
    return dtoObjectMapper.readTree(content);
  }
}
