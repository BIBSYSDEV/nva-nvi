package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.Map;

public record InstitutionStatusAggregationReport(
    String year,
    URI topLevelOrganizationId,
    OrganizationStatusAggregation totals,
    Map<URI, OrganizationStatusAggregation> byOrganization) {}
