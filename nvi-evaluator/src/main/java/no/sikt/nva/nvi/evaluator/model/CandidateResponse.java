package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateResponse(
    @JsonProperty(PUBLICATION_BUCKET_URI) URI publicationBucketUri,
    @JsonProperty(APPROVAL_AFFILIATIONS) List<URI> approvalAffiliations) {

    private static final String PUBLICATION_BUCKET_URI = "publicationBucketUri";
    private static final String APPROVAL_AFFILIATIONS = "approvalAffiliations";
}