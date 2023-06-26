package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;

public record NviCandidateIndexDocument(
    @JsonProperty(CONTEXT) URI context,
    @JsonProperty(IDENTIFIER) String identifier,
    @JsonProperty(YEAR) String year,
    @JsonProperty(TYPE) String type,
    @JsonProperty(PUBLICATION) Publication publication,
    @JsonProperty(AFFILIATIONS) List<Affiliation> affiliations) {

    private static final String IDENTIFIER = "identifier";
    private static final String YEAR = "year";
    private static final String TYPE = "type";
    private static final String PUBLICATION = "publication";
    private static final String AFFILIATIONS = "affiliations";
    private static final String CONTEXT = "context";
}
