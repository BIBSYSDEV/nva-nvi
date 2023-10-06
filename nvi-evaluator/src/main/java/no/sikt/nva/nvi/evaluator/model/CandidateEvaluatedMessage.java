package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record CandidateEvaluatedMessage(
    URI publicationBucketUri,
    CandidateType candidate
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private URI publicationBucketUri;
        private CandidateType candidate;

        private Builder() {
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withCandidateType(CandidateType candidate) {
            this.candidate = candidate;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(publicationBucketUri, candidate);
        }
    }
}