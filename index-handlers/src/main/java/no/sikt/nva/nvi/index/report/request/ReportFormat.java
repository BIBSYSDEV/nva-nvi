package no.sikt.nva.nvi.index.report.request;

import static java.util.Objects.isNull;
import static no.sikt.nva.nvi.index.report.request.ReportType.PUBLICATION_POINTS;
import static nva.commons.apigateway.MediaType.CSV_UTF_8;
import static nva.commons.apigateway.MediaType.JSON_UTF_8;
import static nva.commons.apigateway.MediaType.OOXML_SHEET;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import no.unit.nva.commons.json.JsonSerializable;
import nva.commons.apigateway.MediaType;

public class ReportFormat implements JsonSerializable {

  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      List.of(JSON_UTF_8, OOXML_SHEET, CSV_UTF_8);
  private final MediaType mediaType;
  private final ReportType type;

  public ReportFormat(MediaType mediaType) {
    this.mediaType = mediaType;
    this.type = ReportType.DEFAULT_REPORT;
  }

  @JsonCreator
  public ReportFormat(
      @JsonProperty("mediaType") MediaType mediaType,
      @JsonProperty("reportType") ReportType reportType) {
    this.mediaType = assignMediaType(mediaType);
    this.type = assignReportType(mediaType, reportType);
  }

  public MediaType getMediaType() {
    return mediaType;
  }

  public Optional<ReportType> getType() {
    return Optional.ofNullable(type);
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
    return PUBLICATION_POINTS == type;
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
