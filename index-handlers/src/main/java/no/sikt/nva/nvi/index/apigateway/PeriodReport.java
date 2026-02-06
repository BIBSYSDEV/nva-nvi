package no.sikt.nva.nvi.index.apigateway;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record PeriodReport() implements ReportResponse {}
