package no.sikt.nva.nvi.common.examples;

import java.net.URI;
import no.sikt.nva.nvi.common.dto.PublicationChannelDto;
import no.sikt.nva.nvi.common.model.ScientificValue;

/**
 * Example models for testing purposes, corresponding to the data in
 * /resources/expandedPublications/
 */
public class ExamplePublicationChannels {

  public static final PublicationChannelDto JOURNAL_OF_TESTING =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/013E7484-327D-4F42-ACA4-8F975CCFF34C/2025"))
          .withIdentifier("013E7484-327D-4F42-ACA4-8F975CCFF34C")
          .withChannelType("Journal")
          .withName(
              "IEEE International Conference on Software Testing Verification and Validation"
                  + " Workshop, ICSTW")
          .withYear("2025")
          .withScientificValue(ScientificValue.LEVEL_ONE)
          .withPrintIssn("2159-4848")
          .build();

  public static final PublicationChannelDto PUBLISHER_OF_TESTING =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/publisher/DF3FB68B-F613-4D6F-90D5-38FEC2A61A41/2025"))
          .withIdentifier("DF3FB68B-F613-4D6F-90D5-38FEC2A61A41")
          .withChannelType("Publisher")
          .withName("American Society for Testing & Materials (ASTM) International")
          .withYear("2025")
          .withScientificValue(ScientificValue.LEVEL_ONE)
          .build();

  public static final PublicationChannelDto SERIES_OF_TESTING =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/4DB8ADA8-2031-4092-864B-795432CCBD68/2025"))
          .withIdentifier("4DB8ADA8-2031-4092-864B-795432CCBD68")
          .withChannelType("Series")
          .withName("Beihefte zur Zeitschrift für die alttestamentliche Wissenschaft")
          .withYear("2025")
          .withScientificValue(ScientificValue.LEVEL_ONE)
          .withPrintIssn("0934-2575")
          .build();

  public static final PublicationChannelDto SERIES_OF_ANTHOLOGY =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/65CF2101-E6CE-437F-9DDD-07FC3DB6B119/2017"))
          .withIdentifier("65CF2101-E6CE-437F-9DDD-07FC3DB6B119")
          .withChannelType("Series")
          .withName("Norsk veterinærtidsskrift")
          .withYear("2017")
          .withScientificValue(ScientificValue.LEVEL_ONE)
          .withOnlineIssn("2704-0410")
          .withPrintIssn("0332-5741")
          .build();
}
