package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportType;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.commons.json.JsonUtils;

public record GenerateReportMessage(
    ReportPresignedUrl reportPresignedUrl,
    URI queryId,
    String period,
    URI institutionId,
    ReportType reportType)
    implements JsonSerializable {

  public static GenerateReportMessage from(String json) throws JsonProcessingException {
    return JsonUtils.dtoObjectMapper.readValue(json, GenerateReportMessage.class);
  }

  public static GenerateReportMessage create(
      ReportRequest request, ReportPresignedUrl reportPresignedUrl) {
    return switch (request) {
      case AllInstitutionsReportRequest report ->
          new GenerateReportMessage(
              reportPresignedUrl, report.queryId(), report.period(), null, report.reportType());
      case InstitutionReportRequest report ->
          new GenerateReportMessage(
              reportPresignedUrl,
              report.queryId(),
              report.period(),
              report.institutionId(),
              report.reportType());
      default -> throw new IllegalArgumentException("Unsupported request type: " + request);
    };
  }
}
