package no.sikt.nva.nvi.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetaData(@JsonProperty("index") String index,
                       @JsonProperty("documentIdentifier") String documentIdentifier) {

}
