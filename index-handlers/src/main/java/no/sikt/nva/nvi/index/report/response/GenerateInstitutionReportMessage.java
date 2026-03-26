package no.sikt.nva.nvi.index.report.response;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;
import no.sikt.nva.nvi.report.presigner.ReportPresigner.ReportPresignedUrl;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record GenerateInstitutionReportMessage(
    ReportPresignedUrl reportPresignedUrl, URI queryId, String period, URI institutionId)
    implements GenerateReportMessage {}
