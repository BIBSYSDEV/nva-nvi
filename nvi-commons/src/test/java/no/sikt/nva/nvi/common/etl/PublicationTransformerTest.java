package no.sikt.nva.nvi.common.etl;

import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_1;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_2;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.client.model.Organization;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationTransformerTest {
  private static final String BUCKET_NAME = "testBucket";
  private static final String EXAMPLE_PUBLICATION_1 =
      "expandedPublications/validNviCandidate1.json";
  private static final String EXAMPLE_PUBLICATION_2 =
      "expandedPublications/validNviCandidate2.json";

  private S3Driver s3Driver;
  private PublicationLoader dataLoader;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new PublicationLoader(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  @Test
  void shouldNotFailWhenValidatingExampleDocuments() {
    var actual = parseExampleDocument(EXAMPLE_PUBLICATION_2);
    assertDoesNotThrow(actual::validate);
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
        argumentSet("Minimal example", EXAMPLE_PUBLICATION_1, EXAMPLE_1),
        argumentSet("Full example", EXAMPLE_PUBLICATION_2, EXAMPLE_2));
  }
}
