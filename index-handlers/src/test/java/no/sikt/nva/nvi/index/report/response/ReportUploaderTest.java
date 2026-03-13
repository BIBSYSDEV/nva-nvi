package no.sikt.nva.nvi.index.report.response;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
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
  void shouldCreateMultipartUpload() {
    mockMultipartUpload();

    uploader.upload(largeContent(), "xlsx", "application/xlsx");

    verify(s3Client).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    verify(s3Client).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
  }

  @Test
  void shouldReturnPresignedUrl() {
    mockMultipartUpload();

    var uri = uploader.upload(largeContent(), "xlsx", "application/xlsx");

    assertNotNull(uri);
  }

  private void mockMultipartUpload() {
    when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
        .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload-id").build());
    when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
        .thenReturn(UploadPartResponse.builder().eTag("etag").build());
    when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
        .thenReturn(CompleteMultipartUploadResponse.builder().build());
  }

  private static byte[] largeContent() {
    return new byte[ONE_MB + 1];
  }
}
