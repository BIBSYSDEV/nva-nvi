package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import no.unit.nva.commons.json.JsonSerializable;

public record NviCandidate(@JsonProperty(PUBLICATION_ID) String publicationId,
                           @JsonProperty(S3_URI) String s3Uri,
                           @JsonProperty(APPROVAL_AFFILIATIONS) List<String> approvalAffiliations) implements
                                                                                                   JsonSerializable {

    public static final String PUBLICATION_ID = "publicationId";
    public static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
    public static final String S3_URI = "s3Uri";
}
