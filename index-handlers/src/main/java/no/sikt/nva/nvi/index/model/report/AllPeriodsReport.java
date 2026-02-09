package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record AllPeriodsReport() implements ReportResponse {}
