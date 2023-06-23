package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Contributor(@JsonProperty(ID) String id,
                          @JsonProperty(NAME) String name,
                          @JsonProperty(ORCID) String orcId) {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String ORCID = "orcid";
}
