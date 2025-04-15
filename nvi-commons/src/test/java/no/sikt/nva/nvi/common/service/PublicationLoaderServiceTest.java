package no.sikt.nva.nvi.common.service;

import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_ACADEMIC_CHAPTER_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_1_PATH;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2;
import static no.sikt.nva.nvi.common.examples.ExamplePublications.EXAMPLE_PUBLICATION_2_PATH;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PublicationLoaderServiceTest {
  private static final String BUCKET_NAME = "testBucket";

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
  @MethodSource("exampleDocumentTestProvider")
  void shouldNotFailWhenValidatingExampleDocument(String filename) {
    var actual = parseExampleDocument(filename);
    assertThatNoException().isThrownBy(actual::validate);
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentTestProvider")
  void shouldGetExpectedDataFromExampleDocuments(String filename, PublicationDto expected) {
    var actual = parseExampleDocument(filename);
    assertThat(actual).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(expected);
  }

  @Test
  void shouldNotFailWhenParsingIncompleteDocument() {
    var filePath = "expandedPublications/invalidDraft.json";
    var document = stringFromResources(Path.of(filePath));
    var publicationBucketUri = addToS3(filePath, document);
    assertThatNoException().isThrownBy(() -> dataLoader.extractAndTransform(publicationBucketUri));
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
        argumentSet("Minimal example", EXAMPLE_PUBLICATION_1_PATH, EXAMPLE_PUBLICATION_1),
        argumentSet("Full example", EXAMPLE_PUBLICATION_2_PATH, EXAMPLE_PUBLICATION_2),
        argumentSet("Academic chapter", EXAMPLE_ACADEMIC_CHAPTER_PATH, EXAMPLE_ACADEMIC_CHAPTER));
  }
}
