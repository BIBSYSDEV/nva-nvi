package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonValue;

import java.net.URI;
import java.util.Map;

public record InstitutionStatusAggregationReport(@JsonValue Map<URI, OrganizationStatusAggregation> organizations) {}
