package handlers.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
public record UpsertRequest(@JsonProperty(PUBLICATION_BUCKET_URI) String publicationBucketUri,
                            @JsonProperty(NVI_TYPE) String nviType,
                            @JsonProperty(INSTITUTION_APPROVALS) List<InstitutionApprovals> institutionApprovals) {

    private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
    private static final String INSTITUTION_APPROVALS = "institutionApprovals";
    private static final String NVI_TYPE = "nviType";
}
