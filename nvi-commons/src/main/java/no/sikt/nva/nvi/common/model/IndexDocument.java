package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import no.unit.nva.commons.json.JsonSerializable;

public record IndexDocument(@JsonProperty("metadata") MetaData metadata,
                            @JsonProperty("body") JsonNode body) implements JsonSerializable {

}
