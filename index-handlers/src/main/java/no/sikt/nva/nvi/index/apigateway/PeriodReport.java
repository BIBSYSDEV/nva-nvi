package no.sikt.nva.nvi.index.apigateway;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record PeriodReport() implements ReportResponse {

  @Override
  public URI id() {
    return null;
  }
}
