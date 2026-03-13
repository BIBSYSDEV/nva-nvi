package no.sikt.nva.nvi.index.report.response;

import static java.util.UUID.randomUUID;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class ReportUploader {

  private static final int ONE_MB = 1_048_576;
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
    multipartUpload(content, key, contentType);
    return presign(key);
  }

  private void multipartUpload(byte[] content, String key, String contentType) {
    var uploadId = createMultipartUpload(key, contentType);
    var completedParts = uploadParts(content, key, uploadId);
    completeMultipartUpload(key, uploadId, completedParts);
  }

  private String createMultipartUpload(String key, String contentType) {
    return s3Client
        .createMultipartUpload(
            CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build())
        .uploadId();
  }

  private List<CompletedPart> uploadParts(byte[] content, String key, String uploadId) {
    var completedParts = new ArrayList<CompletedPart>();
    var partNumber = 1;
    for (var offset = 0; offset < content.length; offset += ONE_MB) {
      var length = Math.min(ONE_MB, content.length - offset);
      var partBytes = Arrays.copyOfRange(content, offset, offset + length);
      var response =
          s3Client.uploadPart(
              UploadPartRequest.builder()
                  .bucket(bucketName)
                  .key(key)
                  .uploadId(uploadId)
                  .partNumber(partNumber)
                  .contentLength((long) length)
                  .build(),
              RequestBody.fromBytes(partBytes));
      completedParts.add(
          CompletedPart.builder().partNumber(partNumber).eTag(response.eTag()).build());
      partNumber++;
    }
    return completedParts;
  }

  private void completeMultipartUpload(
      String key, String uploadId, List<CompletedPart> completedParts) {
    s3Client.completeMultipartUpload(
        CompleteMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
            .build());
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
