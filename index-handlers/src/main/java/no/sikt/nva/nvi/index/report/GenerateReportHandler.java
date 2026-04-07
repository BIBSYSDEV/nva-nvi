package no.sikt.nva.nvi.index.report;

import static nva.commons.core.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.Optional;
import no.sikt.nva.nvi.common.service.NviPeriodService;
import no.sikt.nva.nvi.index.aws.OpenSearchClientFactory;
import no.sikt.nva.nvi.index.report.response.GenerateReportMessage;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;

public class GenerateReportHandler implements RequestHandler<SQSEvent, Void> {

  private static final String PARSING_FAILED_MESSAGE = "Could not parse message body: %s";

  private final ReportGenerator reportGenerator;

  @JacocoGenerated
  public GenerateReportHandler() {
    this(
        new ReportGenerator(
            NviPeriodService.defaultNviPeriodService(),
            new ReportDocumentClient(OpenSearchClientFactory.createAuthenticatedClient()),
            ReportAggregationClient.defaultClient(),
            S3Driver.defaultS3Client().build()));
  }

  public GenerateReportHandler(ReportGenerator reportGenerator) {
    this.reportGenerator = reportGenerator;
  }

  @Override
  public Void handleRequest(SQSEvent sqsEvent, Context context) {
    getMessage(sqsEvent).ifPresent(reportGenerator::generateReport);
    return null;
  }

  private Optional<GenerateReportMessage> getMessage(SQSEvent sqsEvent) {
    return Optional.ofNullable(sqsEvent.getRecords().getFirst())
        .map(SQSMessage::getBody)
        .map(this::parseMessage);
  }

  private GenerateReportMessage parseMessage(String body) {
    return attempt(() -> GenerateReportMessage.from(body))
        .orElseThrow(failure -> new RuntimeException(PARSING_FAILED_MESSAGE.formatted(body)));
  }
}
