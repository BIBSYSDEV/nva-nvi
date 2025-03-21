package no.sikt.nva.nvi.common.etl;

import java.net.URI;
import java.util.stream.Stream;
import no.sikt.nva.nvi.common.S3StorageReader;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpandedPublicationTransformerTest {
  private static final String BUCKET_NAME = "testBucket";
  private S3Driver s3Driver;
  private String documentContent;
  private URI documentLocation;
  private ExpandedPublicationTransformer dataLoader;

  @BeforeEach
  void setUp() {
    var s3Client = new FakeS3Client();
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    dataLoader = new ExpandedPublicationTransformer(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  @ParameterizedTest
  @MethodSource("exampleDocumentProvider")
  void shouldTransformExampleDocumentsToExpectedDto() {}

  private static Stream<Arguments> exampleDocumentProvider() {
    //    var expectedPublication = new ExpandedPublication();
    return null;
  }
}
