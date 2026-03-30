package no.sikt.nva.nvi.report.presigner;

import static java.util.UUID.randomUUID;

import java.net.URI;
import java.time.Duration;
import nva.commons.apigateway.MediaType;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class ReportPresigner {

  private static final Duration PRESIGN_DURATION = Duration.ofHours(1);
  private static final String FILE_NAME_FORMAT = "%s.%s";
  private static final String XLSX = "xlsx";
  private static final String CSV = "csv";

  private final S3Presigner s3Presigner;
  private final String bucketName;

  public ReportPresigner(S3Presigner s3Presigner, String bucketName) {
    this.s3Presigner = s3Presigner;
    this.bucketName = bucketName;
  }

  public ReportPresignedUrl presign(MediaType mediaType) {
    var key = createKeyWithExtension(mediaType);
    var request = createRequest(key);
    var response = s3Presigner.presignGetObject(request);
    var presignedUrl = URI.create(response.url().toString());
    return new ReportPresignedUrl(bucketName, key, presignedUrl);
  }

  private GetObjectPresignRequest createRequest(String key) {
    return GetObjectPresignRequest.builder()
        .signatureDuration(PRESIGN_DURATION)
        .getObjectRequest(request -> request.bucket(bucketName).key(key))
        .build();
  }

  private static String createKeyWithExtension(MediaType mediaType) {
    if (MediaType.OOXML_SHEET.equals(mediaType)) {
      return FILE_NAME_FORMAT.formatted(randomUUID(), XLSX);
    }
    if (MediaType.CSV_UTF_8.equals(mediaType)) {
      return FILE_NAME_FORMAT.formatted(randomUUID(), CSV);
    }
    return randomUUID().toString();
  }

  public record ReportPresignedUrl(String bucket, String key, URI presignedUrl) {}
}
