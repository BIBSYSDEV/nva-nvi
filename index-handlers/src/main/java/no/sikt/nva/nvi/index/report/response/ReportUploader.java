package no.sikt.nva.nvi.index.report.response;

import static java.util.UUID.randomUUID;

import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class ReportUploader {

  private static final Duration PRESIGN_DURATION = Duration.ofHours(1);

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final String bucketName;

  public ReportUploader(S3Client s3Client, S3Presigner s3Presigner, String bucketName) {
    this.s3Client = s3Client;
    this.s3Presigner = s3Presigner;
    this.bucketName = bucketName;
  }

  public URI upload(byte[] content, String extension, String contentType) {
    var key = "%s.%s".formatted(randomUUID(), extension);
    putContent(content, key, contentType);
    return presign(key);
  }

  private void putContent(byte[] content, String key, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
        RequestBody.fromBytes(content));
  }

  private URI presign(String key) {
    var presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_DURATION)
            .getObjectRequest(r -> r.bucket(bucketName).key(key))
            .build();
    return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
  }
}
