package no.sikt.nva.nvi.report.presigner;

import static java.util.UUID.randomUUID;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class ReportPresigner {

  private static final Duration PRESIGN_DURATION = Duration.ofHours(1);

  private final S3Presigner s3Presigner;
  private final String bucketName;

  public ReportPresigner(S3Presigner s3Presigner, String bucketName) {
    this.s3Presigner = s3Presigner;
    this.bucketName = bucketName;
  }

  public ReportPresignedUrl presign(Extension extension) {
    var key = createKeyWithExtension(extension);
    var request = createRequest(key);
    var response = s3Presigner.presignGetObject(request);
    var presignedUrl = URI.create(response.url().toString());
    return new ReportPresignedUrl(bucketName, key, extension, presignedUrl);
  }

  private GetObjectPresignRequest createRequest(String key) {
    return GetObjectPresignRequest.builder()
        .signatureDuration(PRESIGN_DURATION)
        .getObjectRequest(request -> request.bucket(bucketName).key(key))
        .build();
  }

  private static String createKeyWithExtension(Extension extension) {
    return "%s.%s".formatted(randomUUID(), extension.getValue());
  }

  public record ReportPresignedUrl(
      String bucket, String key, Extension extension, URI presignedUrl) {}
}
