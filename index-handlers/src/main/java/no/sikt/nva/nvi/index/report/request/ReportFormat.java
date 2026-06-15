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
import java.util.List;
import java.util.Objects;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.apigateway.MediaType;
import nva.commons.core.JacocoGenerated;

public class ReportFormat implements JsonSerializable {

  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      List.of(JSON_UTF_8, OOXML_SHEET, CSV_UTF_8);

  private final MediaType mediaType;

  private final ReportType reportType;

  public ReportFormat(MediaType mediaType, ReportType reportType) {
    this.mediaType = assignMediaType(mediaType);
    this.reportType = assignReportType(mediaType, reportType);
  }

  @JsonCreator
  public ReportFormat(
      @JsonProperty("mediaType") String mediaType,
      @JsonProperty("reportType") ReportType reportType) {
    this.mediaType = assignMediaType(MediaType.parse(mediaType));
    this.reportType = assignReportType(this.mediaType, reportType);
  }

  @JsonIgnore
  public MediaType getMediaType() {
    return mediaType;
  }

  @JsonProperty("mediaType")
  public String mediaType() {
    return mediaType.toString();
  }

  public ReportType getReportType() {
    return reportType;
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

  @JacocoGenerated
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ReportFormat that)) {
      return false;
    }
    return Objects.equals(getMediaType(), that.getMediaType())
        && this.reportType == that.reportType;
  }

  @JacocoGenerated
  @Override
  public int hashCode() {
    return Objects.hash(getMediaType(), getReportType());
  }

  private static ReportType assignReportType(MediaType mediaType, ReportType reportType) {
    if (JSON_UTF_8.equals(mediaType)) {
      return null;
    }
    return isNull(reportType) ? ReportType.AUTHOR_SHARES : reportType;
  }

  private MediaType assignMediaType(MediaType mediaType) {
    if (isNull(mediaType)) {
      return JSON_UTF_8;
    }
    return SUPPORTED_MEDIA_TYPES.contains(mediaType) ? mediaType : JSON_UTF_8;
  }
}
