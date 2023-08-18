package no.sikt.nva.nvi.index.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import nva.commons.core.JacocoGenerated;

@JacocoGenerated
@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record NviCandidateMessageBody(@JsonProperty(PUBLICATION_BUCKET_URI) String publicationBucketUri,
                                      @JsonProperty(APPROVAL_AFFILIATIONS) List<String> affiliationApprovals) {

    private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
    private static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
}
