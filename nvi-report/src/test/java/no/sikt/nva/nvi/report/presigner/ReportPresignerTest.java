package no.sikt.nva.nvi.report.presigner;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import nva.commons.apigateway.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class ReportPresignerTest {

  private static final String BUCKET = "test-bucket";

  private ReportPresigner presigner;

  @BeforeEach
  void setUp() throws MalformedURLException {
    var s3Presigner = mock(S3Presigner.class);
    var presignedObject = mock(PresignedGetObjectRequest.class);
    when(presignedObject.url()).thenReturn(randomUri().toURL());
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedObject);
    presigner = new ReportPresigner(s3Presigner, BUCKET);
  }

  @Test
  void shouldReturnPresignedUrl() {
    var presignedFile = presigner.presign(MediaType.CSV_UTF_8);

    assertInstanceOf(URI.class, presignedFile.presignedUrl());
  }

  @Test
  void shouldReturnKeyWithProvidedExtension() {
    var presignedFile = presigner.presign(MediaType.OOXML_SHEET);

    assertTrue(presignedFile.key().endsWith(".xlsx"));
  }

  @Test
  void shouldReturnBucketName() {
    var result = presigner.presign(MediaType.CSV_UTF_8);

    assertNotNull(result.bucket());
  }
}
