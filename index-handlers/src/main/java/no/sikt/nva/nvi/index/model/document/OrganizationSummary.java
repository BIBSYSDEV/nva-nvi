package no.sikt.nva.nvi.index.model.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.net.URI;
import no.sikt.nva.nvi.common.service.model.GlobalApprovalStatus;

/** Denormalized model to support aggregate queries by organization. */
public record OrganizationSummary(
    URI organizationId,
    // FIXME: Temporary alias for renamed field. Remove after full reindexing is completed.
    @JsonAlias("directPoints") BigDecimal points,
    ApprovalStatus approvalStatus,
    GlobalApprovalStatus globalApprovalStatus) {}
