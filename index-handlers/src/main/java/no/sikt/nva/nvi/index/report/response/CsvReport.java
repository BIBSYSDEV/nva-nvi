package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public record CsvReport(URI id, @JsonValue String content) implements ReportResponse {}
