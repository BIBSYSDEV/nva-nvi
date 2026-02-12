package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated // FIXME: Not in use yet
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record UnitSummary(URI id, DirectAffiliationAggregation statistics) {}
