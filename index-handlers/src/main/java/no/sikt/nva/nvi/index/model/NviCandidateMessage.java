package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NviCandidateMessage(@JsonProperty(PUBLICATION_ID) String publicationId,
                                  @JsonProperty(APPROVAL_AFFILIATIONS) List<String> approvalAffiliations) {

    public static final String PUBLICATION_ID = "publicationId";
    public static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
}
