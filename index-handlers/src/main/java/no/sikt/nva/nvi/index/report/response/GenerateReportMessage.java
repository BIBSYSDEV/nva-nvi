package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.sikt.nva.nvi.index.report.request.AllInstitutionsReportRequest;
import no.sikt.nva.nvi.index.report.request.InstitutionReportRequest;
import no.sikt.nva.nvi.index.report.request.PeriodReportRequest;
import no.sikt.nva.nvi.index.report.request.ReportRequest;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.commons.json.JsonUtils;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(GenerateAllInstitutionsReportMessage.class),
  @JsonSubTypes.Type(GenerateInstitutionReportMessage.class),
  @JsonSubTypes.Type(GeneratePeriodReportMessage.class)
})
public sealed interface GenerateReportMessage extends JsonSerializable
    permits GenerateAllInstitutionsReportMessage,
        GenerateInstitutionReportMessage,
        GeneratePeriodReportMessage {

  ReportPresignedUrl reportPresignedUrl();

  String period();

  static GenerateReportMessage from(String json) throws JsonProcessingException {
    return JsonUtils.dtoObjectMapper.readValue(json, GenerateReportMessage.class);
  }

  static GenerateReportMessage create(
      ReportRequest request, ReportPresignedUrl reportPresignedUrl) {
    return switch (request) {
      case AllInstitutionsReportRequest report ->
          new GenerateAllInstitutionsReportMessage(
              reportPresignedUrl, report.queryId(), report.period());
      case InstitutionReportRequest report ->
          new GenerateInstitutionReportMessage(
              reportPresignedUrl, report.queryId(), report.period(), report.institutionId());
      case PeriodReportRequest report ->
          new GeneratePeriodReportMessage(reportPresignedUrl, report.queryId(), report.period());
      default -> throw new IllegalArgumentException("Unsupported request type: " + request);
    };
  }
}
