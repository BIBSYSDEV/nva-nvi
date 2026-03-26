package no.sikt.nva.nvi.index.report;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportType;
import no.sikt.nva.nvi.index.report.response.GenerateReportMessage;
import no.sikt.nva.nvi.report.presigner.Extension;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import no.unit.nva.stubs.FakeContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GenerateReportHandlerTest {

  private static final FakeContext CONTEXT = new FakeContext();

  private GenerateReportHandler handler;
  private ReportGenerator reportGenerator;

  private static Stream<Arguments> reportMessageProvider() {
    var presignedFile = getPresignedFile();
    return Stream.of(
        Arguments.of(
            GenerateReportMessage.create(
                new AllInstitutionsReportRequest(randomUri(), randomString(), ReportType.CSV),
                presignedFile)),
        Arguments.of(
            GenerateReportMessage.create(
                new InstitutionReportRequest(
                    randomUri(), randomString(), randomUri(), ReportType.CSV),
                presignedFile)),
        Arguments.of(
            GenerateReportMessage.create(
                new PeriodReportRequest(randomUri(), randomString(), ReportType.CSV),
                presignedFile)));
  }

  @BeforeEach
  void setUp() {
    reportGenerator = mock(ReportGenerator.class);
    handler = new GenerateReportHandler(reportGenerator);
  }

  @Test
  void shouldThrowExceptionWhenFailingParsingEventBody() {
    var messageBody = randomString();

    var exception =
        assertThrows(
            RuntimeException.class, () -> handler.handleRequest(sqsEvent(messageBody), CONTEXT));
    assertEquals("Could not parse message body: %s".formatted(messageBody), exception.getMessage());
  }

  @Test
  void shouldThrowExceptionWhenReportGenerationFails() {
    doThrow(RuntimeException.class).when(reportGenerator).generateReport(any());

    assertThrows(
        RuntimeException.class, () -> handler.handleRequest(sqsEvent(validMessage()), CONTEXT));
  }

  @ParameterizedTest
  @MethodSource("reportMessageProvider")
  void shouldNotFailOnSuccess(GenerateReportMessage message) {
    assertDoesNotThrow(() -> handler.handleRequest(sqsEvent(message), CONTEXT));
  }

  private static SQSEvent sqsEvent(String body) {
    var message = new SQSMessage();
    message.setBody(body);
    var event = new SQSEvent();
    event.setRecords(List.of(message));
    return event;
  }

  private static SQSEvent sqsEvent(GenerateReportMessage message) {
    return sqsEvent(message.toJsonString());
  }

  private GenerateReportMessage validMessage() {
    var request = new AllInstitutionsReportRequest(randomUri(), randomString(), ReportType.CSV);
    return GenerateReportMessage.create(request, getPresignedFile());
  }

  private static ReportPresignedUrl getPresignedFile() {
    var key = "%s.csv".formatted(UUID.randomUUID());
    return new ReportPresignedUrl("bucket", key, Extension.CSV, randomUri());
  }
}
