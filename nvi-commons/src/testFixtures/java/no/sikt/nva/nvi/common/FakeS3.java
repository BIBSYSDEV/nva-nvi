package no.sikt.nva.nvi.common;

import static no.sikt.nva.nvi.test.TestConstants.BODY_FIELD;
import static no.unit.nva.testutils.RandomDataGenerator.objectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import no.sikt.nva.nvi.common.service.PublicationLoaderService;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.paths.UnixPath;

public class FakeS3 {
  private static final FakeS3Client s3Client = new FakeS3Client();
  private static final String BUCKET_NAME = "testBucket";
  private final S3Driver s3Driver;
  private final PublicationLoaderService publicationLoader;

  public FakeS3() {
    var storageReader = new S3StorageReader(s3Client, BUCKET_NAME);
    publicationLoader = new PublicationLoaderService(storageReader);
    s3Driver = new S3Driver(s3Client, BUCKET_NAME);
  }

  public PublicationLoaderService getPublicationLoader() {
    return publicationLoader;
  }

  public URI addPublication(String identifier, JsonNode body) {
    try {
      var root = objectMapper.createObjectNode();
      root.set(BODY_FIELD, body);
      return s3Driver.insertFile(UnixPath.of(identifier), root.toString());
    } catch (IOException e) {
      throw new RuntimeException("Failed to add document to S3", e);
    }
  }

  public URI addDocument(String identifier, String document) {
    try {
      return s3Driver.insertFile(UnixPath.of(identifier), document);
    } catch (IOException e) {
      throw new RuntimeException("Failed to add document to S3", e);
    }
  }
}
