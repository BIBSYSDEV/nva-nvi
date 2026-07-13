package no.sikt.nva.nvi.index.report.request;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.report.request.ReportType.AUTHOR_SHARES_CONTROL;
import static no.sikt.nva.nvi.index.report.request.ReportType.PUBLICATION_POINTS;
import static nva.commons.apigateway.MediaType.CSV_UTF_8;
import static nva.commons.apigateway.MediaType.JSON_UTF_8;
import static nva.commons.apigateway.MediaType.OOXML_SHEET;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.apigateway.MediaType;

public record ReportFormat(
    @JsonSerialize(using = ToStringSerializer.class) MediaType mediaType, ReportType reportType)
    implements JsonSerializable {

  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      List.of(JSON_UTF_8, OOXML_SHEET, CSV_UTF_8);

  public ReportFormat {
    mediaType = assignMediaType(mediaType);
    reportType = assignReportType(mediaType, reportType);
  }

  @JsonCreator
  public ReportFormat(
      @JsonProperty("mediaType") String mediaType,
      @JsonProperty("reportType") ReportType reportType) {
    this(MediaType.parse(mediaType), reportType);
  }

  @JsonIgnore
  public boolean isXlsxReport() {
    return OOXML_SHEET.equals(mediaType);
  }

  @JsonIgnore
  public boolean isCsvReport() {
    return CSV_UTF_8.equals(mediaType);
  }

  @JsonIgnore
  public boolean isPublicationPointsReport() {
    return PUBLICATION_POINTS == reportType;
  }

  @JsonIgnore
  public boolean isAuthorSharesControlReport() {
    return AUTHOR_SHARES_CONTROL == reportType;
  }

  private static ReportType assignReportType(MediaType mediaType, ReportType reportType) {
    if (JSON_UTF_8.equals(mediaType)) {
      return null;
    }
    return isNull(reportType) ? ReportType.AUTHOR_SHARES : reportType;
  }

  private static MediaType assignMediaType(MediaType mediaType) {
    if (isNull(mediaType)) {
      return JSON_UTF_8;
    }
    return SUPPORTED_MEDIA_TYPES.contains(mediaType) ? mediaType : JSON_UTF_8;
  }
}
