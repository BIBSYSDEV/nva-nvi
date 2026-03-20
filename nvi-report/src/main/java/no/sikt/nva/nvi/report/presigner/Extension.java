package no.sikt.nva.nvi.report.presigner;

import nva.commons.apigateway.MediaType;

public enum Extension {
  CSV("csv", MediaType.CSV_UTF_8.toString()),
  XLSX("xlsx", MediaType.OOXML_SHEET.toString());

  private final String value;
  private final String mediaType;

  Extension(String value, String mediaType) {
    this.value = value;
    this.mediaType = mediaType;
  }

  public String getValue() {
    return value;
  }

  public String getMediaType() {
    return mediaType;
  }
}
