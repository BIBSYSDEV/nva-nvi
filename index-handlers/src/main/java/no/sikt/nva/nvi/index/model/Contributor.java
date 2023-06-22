package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Contributor(@JsonProperty(ID) String id,
                          @JsonProperty(NAME) String name,
                          @JsonProperty(ORCID) String orcId) {

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String ORCID = "orcid";
}
