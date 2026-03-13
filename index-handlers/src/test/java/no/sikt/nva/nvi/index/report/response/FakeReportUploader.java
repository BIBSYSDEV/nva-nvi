package no.sikt.nva.nvi.index.report.response;

import java.net.URI;

public class FakeReportUploader extends ReportUploader {

  public static final URI PRESIGNED_URI = URI.create("https://s3.example.com/nvi-reports/report");

  public FakeReportUploader() {
    super(null, null, null);
  }

  @Override
  public URI upload(byte[] content, String extension, String contentType) {
    return PRESIGNED_URI;
  }
}
