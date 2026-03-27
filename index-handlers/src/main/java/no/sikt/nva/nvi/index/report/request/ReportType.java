package no.sikt.nva.nvi.index.report.request;

import java.util.Optional;
import nva.commons.apigateway.MediaType;

public enum ReportType {
  JSON(null),
  XLSX_AUTHOR_SHARES("author-shares"),
  CSV_AUTHOR_SHARES("author-shares"),
  XLSX_PUBLICATION_POINTS("publication-points"),
  CSV_PUBLICATION_POINTS("publication-points");

  private static final String AUTHOR_SHARES = "author-shares";
  private static final String PUBLICATION_POINTS = "publication-points";
  private final String profile;

  ReportType(String profile) {
    this.profile = profile;
  }

  public boolean isPublicationPoints() {
    return PUBLICATION_POINTS.equals(getProfile());
  }

  public static ReportType create(MediaType mediaType, String profile) {
    var effectiveMediaType = Optional.ofNullable(mediaType);
    var effectiveProfile = Optional.ofNullable(profile).orElse(AUTHOR_SHARES);

    if (effectiveMediaType.isEmpty()) {
      return JSON;
    }
    if (MediaType.OOXML_SHEET.equals(effectiveMediaType.get())) {
      return PUBLICATION_POINTS.equals(effectiveProfile)
          ? XLSX_PUBLICATION_POINTS
          : XLSX_AUTHOR_SHARES;
    }
    if (MediaType.CSV_UTF_8.equals(effectiveMediaType.get())) {
      return PUBLICATION_POINTS.equals(effectiveProfile)
          ? CSV_PUBLICATION_POINTS
          : CSV_AUTHOR_SHARES;
    }
    return JSON;
  }

  public String getProfile() {
    return profile;
  }
}
