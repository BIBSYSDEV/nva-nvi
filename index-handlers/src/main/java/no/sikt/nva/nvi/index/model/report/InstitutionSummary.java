package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.net.URI;

@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record InstitutionSummary(URI id, String name, String sector, TopLevelAggregation summary) {}
