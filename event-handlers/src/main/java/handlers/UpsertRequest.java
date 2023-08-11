package handlers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import nva.commons.core.JacocoGenerated;

//TODO: Remove jacocoGenerated when rest of the tests cases are implemented
@JsonSerialize
@JacocoGenerated
public record UpsertRequest(@JsonProperty(PUBLICATION_BUCKET_URI) String publicationBucketUri,
                            @JsonProperty(APPROVAL_AFFILIATIONS) List<String> affiliationApprovals) {

    private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
    private static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
}
