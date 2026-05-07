package no.sikt.nva.nvi.index.report.response;

import no.sikt.nva.nvi.common.queue.QueueClient;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import no.sikt.nva.nvi.report.presigner.ReportPresigner;
import nva.commons.apigateway.MediaType;
import nva.commons.core.Environment;

public class PresignReportService {

  private static final String REPORT_QUEUE = "REPORT_QUEUE";
  private final ReportPresigner reportPresigner;
  private final QueueClient queueClient;
  private final String reportQueueUrl;

  public PresignReportService(
      ReportPresigner reportPresigner, QueueClient queueClient, Environment environment) {
    this.reportPresigner = reportPresigner;
    this.queueClient = queueClient;
    this.reportQueueUrl = environment.readEnv(REPORT_QUEUE);
  }

  public PresignedReport presign(ReportRequest request, MediaType mediaType) {
    var presignedFile = reportPresigner.presign(mediaType);
    queueClient.sendMessage(
        GenerateReportMessage.create(request, presignedFile).toJsonString(), reportQueueUrl);
    return new PresignedReport(request.queryId(), presignedFile.presignedUrl());
  }
}
