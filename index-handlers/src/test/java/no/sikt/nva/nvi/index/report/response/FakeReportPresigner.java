package no.sikt.nva.nvi.index.report.response;

import static java.util.UUID.randomUUID;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;

import java.net.URI;
import no.sikt.nva.nvi.report.presigner.Extension;
import no.sikt.nva.nvi.report.presigner.ReportPresigner;

public class FakeReportPresigner extends ReportPresigner {

  public static final URI PRESIGNED_URI = randomUri();

  public FakeReportPresigner() {
    super(null, null);
  }

  @Override
  public ReportPresignedUrl presign(Extension extension) {
    return new ReportPresignedUrl(
        randomString(),
        "%s.%s".formatted(randomUUID(), extension.getValue()),
        extension,
        PRESIGNED_URI);
  }
}
