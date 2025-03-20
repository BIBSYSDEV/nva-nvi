package no.sikt.nva.nvi.common.etl;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

class PublicationChannelTest {

  String exampleJson =
      """
{
    "id" : "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/82B1FF7F-D85C-4489-81CC-0EA767EFA122/2025",
    "type" : "PublicationChannelTemp",
    "channelType" : "Journal",
    "identifier" : "82B1FF7F-D85C-4489-81CC-0EA767EFA122",
    "name" : "American Concrete Institute. Publication SP",
    "printIssn" : "0193-2527",
    "sameAs" : "https://kanalregister.hkdir.no/publiseringskanaler/info/tidsskrift?pid=82B1FF7F-D85C-4489-81CC-0EA767EFA122",
    "scientificValue" : "LevelOne",
    "year" : "2023"
  }

""";

  @Test
  void shouldRoundTrip() throws JsonProcessingException {
    var channel = dtoObjectMapper.readValue(exampleJson, PublicationChannelTemp.class);
    var foo = 2;
  }
}
