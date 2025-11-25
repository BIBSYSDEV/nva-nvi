package no.sikt.nva.nvi.index.model.report;

import java.net.URI;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

// TODO: Not in use yet, intended for NP-50248
@JacocoGenerated
public record InstitutionStatusAggregationReport(
    String year,
    URI topLevelOrganizationId,
    OrganizationStatusAggregation totals,
    Map<URI, OrganizationStatusAggregation> byOrganization) {}
