package no.sikt.nva.nvi.common.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// TODO: NP-51402 - after data is migrated
public record PublicationChannelResponse(
    @JsonProperty("name") String name, @JsonProperty("printIssn") String printIssn) {}
