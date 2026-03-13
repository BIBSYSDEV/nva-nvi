package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonSubTypes;

@JsonSubTypes({@JsonSubTypes.Type(value = InstitutionJsonReport.class, name = "InstitutionReport")})
public sealed interface InstitutionReport extends ReportResponse permits InstitutionJsonReport {}
