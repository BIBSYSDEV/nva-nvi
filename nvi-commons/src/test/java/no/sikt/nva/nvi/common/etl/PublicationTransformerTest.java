package no.sikt.nva.nvi.common.etl;

import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationTransformerTest {
  private static final String BUCKET_NAME = "testBucket";

  private static final PublicationChannelDto EXAMPLE_1_CHANNEL =
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
          .withScientificValue("LevelOne")
          .withPrintIssn("2159-4848")
          .build();

  private static final Map<String, String> SIKT_LABELS =
      Map.of(
          "nb",
          "Sikt – Kunnskapssektorens tjenesteleverandør",
          "en",
          "Sikt - Norwegian Agency for Shared Services in Education and Research");

  private static final Map<String, String> SIKT_SUBUNIT_LABELS =
      Map.of(
          "nn", "Divisjon for forskings- og kunnskapsressursar",
          "nb", "Divisjon forsknings- og kunnskapsressurser",
          "en", "The Research and Education Resources Division");

  private static final Map<String, String> NTNU_LABELS =
      Map.of(
          "nn", "Noregs teknisk-naturvitskaplege universitet",
          "nb", "Norges teknisk-naturvitenskapelige universitet",
          "en", "Norwegian University of Science and Technology");

  private static final URI SIKT_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.0.0.0");
  private static final URI SIKT_SUBUNIT_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/20754.2.0" + ".0");
  private static final URI NTNU_ID =
      URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/194.0.0.0");

  private static final Organization TOP_LEVEL_ORGANIZATION_SIKT =
      Organization.builder()
          .withId(SIKT_ID)
          .withLabels(SIKT_LABELS)
          .withType("Organization")
          .withHasPart(
              List.of(
                  Organization.builder()
                      .withId(SIKT_SUBUNIT_ID)
                      .withLabels(SIKT_SUBUNIT_LABELS)
                      .withType("Organization")
                      .withPartOf(List.of(Organization.builder().withId(SIKT_ID).build()))
                      .build()))
          .build();
  private static final ContributorDto EXAMPLE_2_CONTRIBUTOR_3 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685065"))
          .withName("Donald Duck")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_SIKT))
          .build();
  private static final Organization TOP_LEVEL_ORGANIZATION_NTNU =
      Organization.builder()
          .withId(NTNU_ID)
          .withLabels(NTNU_LABELS)
          .withType("Organization")
          .build();
  private static final ContributorDto EXAMPLE_2_CONTRIBUTOR_1 =
      ContributorDto.builder()
          .withName("Petter Smart")
          .withRole("Creator")
          .withVerificationStatus("NotVerified")
          .withAffiliations(List.of(TOP_LEVEL_ORGANIZATION_NTNU))
          .build();
  private static final Organization EXAMPLE_TOP_LEVEL_ORGANIZATION_3 =
      Organization.builder()
          .withId(
              URI.create("https://api.sandbox.nva.aws.unit.no/cristin/organization/35900068.0.0.0"))
          .withType("Organization")
          .withLabels(Map.of("nb", "University of Cape Town"))
          .build();
  private static final Organization SUB_ORGANIZATION_SIKT =
      Organization.builder()
          .withId(SIKT_SUBUNIT_ID)
          .withLabels(SIKT_SUBUNIT_LABELS)
          .withType("Organization")
          .withPartOf(
              List.of(
                  Organization.builder()
                      .withId(SIKT_ID)
                      .withLabels(SIKT_LABELS)
                      .withType("Organization")
                      .withHasPart(List.of(Organization.builder().withId(SIKT_SUBUNIT_ID).build()))
                      .build()))
          .build();
  private static final ContributorDto EXAMPLE_1_CONTRIBUTOR =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();
  private static final Publication EXAMPLE_1 =
      Publication.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication/0195c6f1a431-6290c69b-5488-44ea-b20f-cef3464fb1b5"))
          .withIdentifier("0195c6f1a431-6290c69b-5488-44ea-b20f-cef3464fb1b5")
          .withTitle("Example NVI candidate #1")
          .withPublicationDate(new PublicationDateDto("2025", null, null))
          .withStatus("PUBLISHED")
          .withPublicationType("AcademicArticle")
          .withModifiedDate(Instant.parse("2025-03-24T06:59:56.170369925Z"))
          .withLanguage("http://lexvo.org/id/iso639-3/nob")
          .withPublicationChannels(List.of(EXAMPLE_1_CHANNEL))
          .withContributors(List.of(EXAMPLE_1_CONTRIBUTOR))
          .withIsInternationalCollaboration(false)
          .withTopLevelOrganizations(
              List.of(TOP_LEVEL_ORGANIZATION_NTNU, TOP_LEVEL_ORGANIZATION_SIKT))
          .build();
  private static final ContributorDto EXAMPLE_2_CONTRIBUTOR_4 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1685046"))
          .withName("Skrue McDuck")
          .withRole("ContactPerson")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT))
          .build();
  private static final ContributorDto EXAMPLE_2_CONTRIBUTOR_5 =
      ContributorDto.builder()
          .withId(URI.create("https://api.sandbox.nva.aws.unit.no/cristin/person/1215176"))
          .withName("Ola Nordmann")
          .withRole("Creator")
          .withVerificationStatus("Verified")
          .withAffiliations(List.of(SUB_ORGANIZATION_SIKT, TOP_LEVEL_ORGANIZATION_NTNU))
          .build();
  private static final ContributorDto EXAMPLE_2_CONTRIBUTOR_2 =
      ContributorDto.builder()
          .withName("John Doe")
          .withRole("Creator")
          .withVerificationStatus("NotVerified")
          .withAffiliations(List.of(EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .build();
  private static final PublicationChannelDto EXAMPLE_2_PUBLISHER =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/publisher/DF3FB68B-F613-4D6F-90D5-38FEC2A61A41/2025"))
          .withIdentifier("DF3FB68B-F613-4D6F-90D5-38FEC2A61A41")
          .withChannelType("Publisher")
          .withName("American Society for Testing & Materials (ASTM) International")
          .withYear("2025")
          .withScientificValue("LevelOne")
          .build();
  private static final PublicationChannelDto EXAMPLE_2_SERIES =
      PublicationChannelDto.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication-channels-v2/serial-publication/4DB8ADA8-2031-4092-864B-795432CCBD68/2025"))
          .withIdentifier("4DB8ADA8-2031-4092-864B-795432CCBD68")
          .withChannelType("Series")
          .withName("Beihefte zur Zeitschrift für die alttestamentliche Wissenschaft")
          .withYear("2025")
          .withScientificValue("LevelOne")
          .withPrintIssn("0934-2575")
          .build();
  private static final Publication EXAMPLE_2 =
      Publication.builder()
          .withId(
              URI.create(
                  "https://api.sandbox.nva.aws.unit.no/publication/0195c6f37392-68057afa-9b9f-4e7a-8c9a-f5aef6b657be"))
          .withIdentifier("0195c6f37392-68057afa-9b9f-4e7a-8c9a-f5aef6b657be")
          .withTitle("Example NVI candidate #2")
          .withPublicationDate(new PublicationDateDto("2025", "3", "24"))
          .withStatus("PUBLISHED")
          .withPublicationType("AcademicMonograph")
          .withModifiedDate(Instant.parse("2025-03-24T08:23:24.859620342Z"))
          .withLanguage("http://lexvo.org/id/iso639-3/eng")
          .withPublicationChannels(List.of(EXAMPLE_2_PUBLISHER, EXAMPLE_2_SERIES))
          .withIsInternationalCollaboration(true)
          .withContributors(
              List.of(
                  EXAMPLE_2_CONTRIBUTOR_1,
                  EXAMPLE_2_CONTRIBUTOR_2,
                  EXAMPLE_2_CONTRIBUTOR_3,
                  EXAMPLE_2_CONTRIBUTOR_4,
                  EXAMPLE_2_CONTRIBUTOR_5))
          .withTopLevelOrganizations(
              List.of(
                  TOP_LEVEL_ORGANIZATION_SIKT,
                  TOP_LEVEL_ORGANIZATION_NTNU,
                  EXAMPLE_TOP_LEVEL_ORGANIZATION_3))
          .build();
  private S3Driver s3Driver;
  private PublicationLoader dataLoader;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new PublicationLoader(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentTestProvider")
  void shouldGetExpectedFieldsFromExampleDocument(String filename, Publication expected) {
    var actual = parseExampleDocument(filename);

    assertEquals(expected.id(), actual.id());
    assertEquals(expected.identifier(), actual.identifier());
    assertEquals(expected.title(), actual.title());
    assertEquals(expected.publicationDate(), actual.publicationDate());
    assertEquals(expected.status(), actual.status());
    assertEquals(expected.modifiedDate(), actual.modifiedDate());
    assertEquals(expected.isInternationalCollaboration(), actual.isInternationalCollaboration());
  }

  private Publication parseExampleDocument(String filename) {
    var document = stringFromResources(Path.of(filename));
    var publicationBucketUri = addToS3(filename, document);
    return dataLoader.extractAndTransform(publicationBucketUri);
  }

  private URI addToS3(String identifier, String document) {
    try {
      return s3Driver.insertFile(UnixPath.of(identifier), document);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add publication to S3", e);
    }
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentTestProvider")
  void shouldGetExpectedContributorsFromExampleDocument(String filename, Publication expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual.contributors(), hasSize(expected.contributors().size()));
    for (ContributorDto contributor : expected.contributors()) {
      Assertions.assertThat(actual.contributors())
          .filteredOn("name", contributor.name())
          .containsOnly(contributor);
    }
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentTestProvider")
  void shouldGetExpectedPublicationChannelsFromExampleDocument(
      String filename, Publication expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual.publicationChannels(), hasSize(expected.publicationChannels().size()));
    for (PublicationChannelDto channel : expected.publicationChannels()) {
      Assertions.assertThat(actual.publicationChannels())
          .filteredOn("channelType", channel.channelType())
          .containsOnly(channel);
    }
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentTestProvider")
  void shouldGetExpectedTopLevelOrganizationsFromExampleDocument(
      String filename, Publication expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual.topLevelOrganizations(), hasSize(expected.topLevelOrganizations().size()));
    for (Organization organization : expected.topLevelOrganizations()) {
      Assertions.assertThat(actual.topLevelOrganizations())
          .filteredOn("id", organization.id())
          .containsOnly(organization);
    }
  }

  private static Stream<Arguments> exampleDocumentTestProvider() {

    return Stream.of(
        argumentSet("Minimal example", "expandedPublications/validNviCandidate1.json", EXAMPLE_1),
        argumentSet("Full example", "expandedPublications/validNviCandidate2.json", EXAMPLE_2));
  }
}
