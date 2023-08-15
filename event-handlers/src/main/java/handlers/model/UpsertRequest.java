package handlers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
public record UpsertRequest(@JsonProperty(PUBLICATION_BUCKET_URI) String publicationBucketUri,
                            @JsonProperty(APPROVAL_AFFILIATIONS) List<String> approvalAffiliations) {

    private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
    private static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
}
