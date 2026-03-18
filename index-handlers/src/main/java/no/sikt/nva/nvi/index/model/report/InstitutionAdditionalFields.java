package no.sikt.nva.nvi.index.model.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InstitutionAdditionalFields(
    @JsonProperty("hk_dir_identifier") String hkdirIdentifier,
    String sector,
    boolean isRbo,
    @JsonProperty("nsdstedkode") String nsdInstitutionIdentifier) {}
