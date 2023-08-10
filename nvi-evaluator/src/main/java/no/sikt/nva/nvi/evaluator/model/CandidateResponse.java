package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import java.util.Set;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateResponse(URI publicationBucketUri,
                                Set<URI> approvalAffiliations) {

}