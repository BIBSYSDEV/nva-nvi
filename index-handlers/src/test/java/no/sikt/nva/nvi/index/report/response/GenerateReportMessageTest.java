package no.sikt.nva.nvi.index.report.response;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportFormat;
import no.sikt.nva.nvi.index.report.request.ReportType;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import nva.commons.apigateway.MediaType;
import org.junit.jupiter.api.Test;

class GenerateReportMessageTest {

  @Test
  void shouldDoRoundTripWithoutLossOfInformation() throws JsonProcessingException {
    var message =
        GenerateReportMessage.create(institutionReportRequest(), randomReportPresignedUrl());
    var json = message.toJsonString();
    var roundTripped = GenerateReportMessage.from(json);

    assertEquals(message, roundTripped);
  }

  private static InstitutionReportRequest institutionReportRequest() {
    return new InstitutionReportRequest(
        randomUri(), randomString(), randomUri(), randomReportFormat());
  }

  private static ReportFormat randomReportFormat() {
    return new ReportFormat(MediaType.CSV_UTF_8, ReportType.PUBLICATION_POINTS);
  }

  private static ReportPresignedUrl randomReportPresignedUrl() {
    return new ReportPresignedUrl(randomString(), randomString(), randomUri());
  }
}
