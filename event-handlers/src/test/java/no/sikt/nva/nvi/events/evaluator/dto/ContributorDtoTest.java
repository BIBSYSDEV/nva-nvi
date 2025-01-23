package no.sikt.nva.nvi.events.evaluator.dto;

import static java.util.Collections.emptyList;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class ContributorDtoTest {

  private static final String UNAFFILIATED_UNVERIFIED_CONTRIBUTOR =
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

  @Test
  void fromJsonNode() throws JsonProcessingException {
    var contributorNode = parseJsonString(UNAFFILIATED_UNVERIFIED_CONTRIBUTOR);
    var expectedContributor =
        new ContributorDto("I.N. Kognito", null, "NotVerified", "Creator", emptyList());
    var actualContributor = ContributorDto.fromJsonNode(contributorNode);
    assertEquals(expectedContributor, actualContributor);
  }

  private JsonNode parseJsonString(String content) throws JsonProcessingException {
    return dtoObjectMapper.readTree(content);
  }
}
