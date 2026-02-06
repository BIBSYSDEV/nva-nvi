package no.sikt.nva.nvi.index.apigateway;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSerialize
public record AllInstitutionsReport() implements ReportResponse {

  @Override
  public URI id() {
    return null;
  }
}
