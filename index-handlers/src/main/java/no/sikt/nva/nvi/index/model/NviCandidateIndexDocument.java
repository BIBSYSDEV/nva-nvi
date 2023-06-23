package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NviCandidateIndexDocument(@JsonProperty(IDENTIFIER) String identifier,
                                        @JsonProperty(YEAR) String year,
                                        @JsonProperty(TYPE) String type,
                                        @JsonProperty(PUBLICATION) Publication publication,
                                        @JsonProperty(AFFILIATIONS) List<Affiliation> affiliations) {

    public static final String IDENTIFIER = "identifier";
    public static final String YEAR = "year";
    public static final String TYPE = "type";
    public static final String PUBLICATION = "publication";
    public static final String AFFILIATIONS = "affiliations";
}
