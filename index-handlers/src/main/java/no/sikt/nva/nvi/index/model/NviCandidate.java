package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

public record NviCandidate(@JsonProperty(PUBLICATION_ID) String publicationId,
                           @JsonProperty(APPROVAL_AFFILIATIONS) List<String> affiliationApprovals) implements
                                                                                                   JsonSerializable {

    public static final String PUBLICATION_ID = "publicationId";
    public static final String APPROVAL_AFFILIATIONS = "affiliationApprovals";
}
