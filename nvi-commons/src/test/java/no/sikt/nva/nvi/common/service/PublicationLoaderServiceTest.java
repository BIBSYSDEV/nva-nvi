package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_1;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_2;
import static nva.commons.core.ioutils.IoUtils.stringFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.sikt.nva.nvi.common.dto.PublicationDto;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationLoaderServiceTest {
  private static final String BUCKET_NAME = "testBucket";
  private static final String EXAMPLE_PUBLICATION_1 =
      "expandedPublications/validNviCandidate1.json";
  private static final String EXAMPLE_PUBLICATION_2 =
      "expandedPublications/validNviCandidate2.json";
  private static final String EXAMPLE_PROVIDER = "exampleDocumentTestProvider";

  private S3Driver s3Driver;
  private PublicationLoaderService dataLoader;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new PublicationLoaderService(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  @ParameterizedTest
  @MethodSource(EXAMPLE_PROVIDER)
  void shouldNotFailWhenValidatingExampleDocument(String filename) {
    var actual = parseExampleDocument(filename);
    assertThatNoException().isThrownBy(() -> actual.validate());
  }

  @ParameterizedTest
  @MethodSource(EXAMPLE_PROVIDER)
  void shouldGetExpectedFieldsFromExampleDocument(String filename, PublicationDto expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual)
        .usingRecursiveComparison()
        .ignoringFields("publicationChannels", "contributors", "topLevelOrganizations")
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource(EXAMPLE_PROVIDER)
  void shouldGetExpectedContributorsFromExampleDocument(String filename, PublicationDto expected) {
    var expectedContributors = expected.contributors();
    var actualContributors = parseExampleDocument(filename).contributors();
    assertThat(actualContributors).containsExactlyInAnyOrderElementsOf(expectedContributors);
  }

  @ParameterizedTest
  @MethodSource(EXAMPLE_PROVIDER)
  void shouldGetExpectedPublicationChannelsFromExampleDocument(
      String filename, PublicationDto expected) {
    var expectedChannels = expected.publicationChannels();
    var actualChannels = parseExampleDocument(filename).publicationChannels();
    assertThat(actualChannels).containsExactlyInAnyOrderElementsOf(expectedChannels);
  }

  @ParameterizedTest
  @MethodSource(EXAMPLE_PROVIDER)
  void shouldGetExpectedTopLevelOrganizationsFromExampleDocument(
      String filename, PublicationDto expected) {
    var expectedOrganizations = expected.topLevelOrganizations();
    var actualOrganizations = parseExampleDocument(filename).topLevelOrganizations();
    assertThat(actualOrganizations).containsExactlyInAnyOrderElementsOf(expectedOrganizations);
  }

  private PublicationDto parseExampleDocument(String filename) {
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

  private static Stream<Arguments> exampleDocumentTestProvider() {

    return Stream.of(
        argumentSet("Minimal example", EXAMPLE_PUBLICATION_1, EXAMPLE_1),
        argumentSet("Full example", EXAMPLE_PUBLICATION_2, EXAMPLE_2));
  }
}
