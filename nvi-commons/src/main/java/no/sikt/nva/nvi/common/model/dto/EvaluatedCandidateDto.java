package no.sikt.nva.nvi.common.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import nva.commons.core.JacocoGenerated;

@JsonSerialize
//TODO: Remove JacocoGenerated when use of publicationBucketUri is implemented
@JacocoGenerated
public record EvaluatedCandidateDto(@JsonProperty(PUBLICATION_BUCKET_URI_FIELD) URI publicationBucketUri,
                                    @JsonProperty(TYPE_FIELD) String type,
                                    @JsonProperty(APPROVAL_AFFILIATIONS) CandidateDetailsDto candidateDetailsDto) {

    public static final String TYPE_FIELD = "type";
    private static final String PUBLICATION_BUCKET_URI_FIELD = "publicationBucketUri";
    private static final String APPROVAL_AFFILIATIONS = "candidateDetails";
}
