package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JsonSerialize
public record NviCandidateIndexDocument(@JsonProperty(CONTEXT) URI context,
                                        String identifier,
                                        String year,
                                        String type,
                                        PublicationDetails publicationDetails,
                                        List<Affiliation> affiliations) {

    private static final String CONTEXT = "@context";
}
