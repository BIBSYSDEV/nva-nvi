package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public record XlsxReport(URI id, URI uri) implements ReportResponse {}
