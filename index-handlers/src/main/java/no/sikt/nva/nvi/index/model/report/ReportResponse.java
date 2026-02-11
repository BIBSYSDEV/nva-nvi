package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import no.unit.nva.commons.json.JsonSerializable;

import java.net.URI;

@JsonSubTypes({
  @JsonSubTypes.Type(value = AllInstitutionsReport.class, name = "AllInstitutionsReport"),
  @JsonSubTypes.Type(value = InstitutionReport.class, name = "InstitutionReport"),
  @JsonSubTypes.Type(value = AllPeriodsReport.class, name = "AllPeriodsReport"),
  @JsonSubTypes.Type(value = PeriodReport.class, name = "PeriodReport")
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface ReportResponse extends JsonSerializable
    permits AllInstitutionsReport, InstitutionReport, AllPeriodsReport, PeriodReport {

    URI id();
}
