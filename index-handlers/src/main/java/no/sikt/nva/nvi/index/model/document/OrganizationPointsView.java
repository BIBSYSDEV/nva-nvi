package no.sikt.nva.nvi.index.model.document;

import java.math.BigDecimal;
import java.net.URI;

public record OrganizationPointsView(URI organizationId, BigDecimal directPoints) {}
