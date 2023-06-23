package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicationChannel(@JsonProperty(ID) String id,
                                 @JsonProperty(NAME) String name,
                                 @JsonProperty(LEVEL) String level,
                                 @JsonProperty(TYPE) String type) {

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String LEVEL = "level";
    private static final String TYPE = "type";
}
