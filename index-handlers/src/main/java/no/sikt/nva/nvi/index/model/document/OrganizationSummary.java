package no.sikt.nva.nvi.index.model.document;

import java.math.BigDecimal;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;

/** Denormalized model to support aggregate queries by organization. */
public record OrganizationSummary(
    URI organizationId,
    BigDecimal points,
    ApprovalStatus approvalStatus,
    GlobalApprovalStatus globalApprovalStatus) {}
