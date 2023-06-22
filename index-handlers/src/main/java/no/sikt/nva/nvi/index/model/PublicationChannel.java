package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicationChannel(@JsonProperty(ID) String id,
                                 @JsonProperty(NAME) String name,
                                 @JsonProperty(LEVEL) String level,
                                 @JsonProperty(TYPE) String type) {

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String LEVEL = "level";
    public static final String TYPE = "type";
}
