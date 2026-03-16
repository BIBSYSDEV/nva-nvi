package no.sikt.nva.nvi.index.report.response;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class ReportUploaderTest {

  private static final String BUCKET = "test-bucket";
  private static final URI EXPECTED_URI = URI.create("https://s3.example.com/report.csv");
  private static final int ONE_MB = 1_048_576;

  private S3Client s3Client;
  private ReportUploader uploader;

  @BeforeEach
  void setUp() throws MalformedURLException {
    s3Client = mock(S3Client.class);
    var s3Presigner = mock(S3Presigner.class);
    var presignedObject = mock(PresignedGetObjectRequest.class);
    when(presignedObject.url()).thenReturn(EXPECTED_URI.toURL());
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedObject);
    uploader = new ReportUploader(s3Client, s3Presigner, BUCKET);
  }

  @Test
  void shouldPersistReport() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    uploader.upload(new byte[ONE_MB], "xlsx", "application/xlsx");

    verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(s3Client, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
  }

  @Test
  void shouldReturnPresignedUrl() {
    when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());

    var uri = uploader.upload(new byte[ONE_MB], "xlsx", "application/xlsx");

    assertNotNull(uri);
  }
}
