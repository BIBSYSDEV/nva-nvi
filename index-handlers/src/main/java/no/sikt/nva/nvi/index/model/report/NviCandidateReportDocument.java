package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;

@JsonSerialize
public record NviCandidateReportDocument(
    UUID identifier,
    ReportingPeriod reportingPeriod,
    GlobalApprovalStatus globalApprovalStatus,
    BigDecimal publicationTypeChannelLevelPoints,
    BigDecimal internationalCollaborationFactor,
    int creatorShareCount,
    ReportPublicationDetails publicationDetails,
    List<ReportApproval> approvals) {}
