package no.sikt.nva.nvi.index.model.report;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;
import no.sikt.nva.nvi.index.model.document.ReportingPeriod;

public record ReportDocument(
    UUID identifier,
    ReportingPeriod reportingPeriod,
    GlobalApprovalStatus globalApprovalStatus,
    BigDecimal publicationTypeChannelLevelPoints,
    BigDecimal internationalCollaborationFactor,
    int creatorShareCount,
    ReportPublicationDetails publicationDetails,
    List<ReportApproval> approvals) {}
