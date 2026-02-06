package no.sikt.nva.nvi.index.apigateway;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;

@JsonSubTypes({
  @JsonSubTypes.Type(value = AllInstitutionsReport.class, name = "AllInstitutionsReport"),
  @JsonSubTypes.Type(value = InstitutionReport.class, name = "InstitutionReport"),
  @JsonSubTypes.Type(value = PeriodReport.class, name = "PeriodReport")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface ReportResponse {

  URI id();
}
