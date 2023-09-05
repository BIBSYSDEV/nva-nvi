package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import no.sikt.nva.nvi.evaluator.model.NviCandidate.CandidateDetails;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateEvaluatedMessage(
    CandidateStatus status,
    URI publicationBucketUri,
    CandidateDetails candidateDetails,
    Map<URI, BigDecimal> institutionPoints
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private CandidateStatus status;
        private URI publicationBucketUri;
        private CandidateDetails candidateDetails;
        private Map<URI, BigDecimal> institutionPoints;

        private Builder() {
        }

        public Builder withStatus(CandidateStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationBucketUri) {
            this.publicationBucketUri = publicationBucketUri;
            return this;
        }

        public Builder withCandidateDetails(CandidateDetails candidateDetails) {
            this.candidateDetails = candidateDetails;
            return this;
        }

        public Builder withInstitutionPoints(Map<URI, BigDecimal> institutionPoints) {
            this.institutionPoints = institutionPoints;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(status, publicationBucketUri, candidateDetails, institutionPoints);
        }
    }
}