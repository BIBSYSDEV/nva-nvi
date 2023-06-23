package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

public record NviCandidate(@JsonProperty(PUBLICATION_ID) String publicationId,
                           @JsonProperty(APPROVAL_AFFILIATIONS) List<String> affiliationApprovals) implements
                                                                                                   JsonSerializable {

    private static final String PUBLICATION_ID = "publicationId";
    private static final String APPROVAL_AFFILIATIONS = "affiliationApprovals";
}
