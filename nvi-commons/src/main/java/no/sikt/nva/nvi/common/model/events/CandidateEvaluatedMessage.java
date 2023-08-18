package no.sikt.nva.nvi.common.model.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import no.sikt.nva.nvi.common.model.events.NviCandidate.CandidateDetails;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateEvaluatedMessage(CandidateStatus status,
                                        URI publicationUri,
                                        CandidateDetails candidateDetails) {

    public static final class Builder {

        private CandidateStatus status;
        private URI publicationUri;
        private CandidateDetails candidateDetails;

        public Builder() {
        }

        public Builder withStatus(CandidateStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPublicationBucketUri(URI publicationUri) {
            this.publicationUri = publicationUri;
            return this;
        }

        public Builder withCandidateDetails(CandidateDetails candidateDetails) {
            this.candidateDetails = candidateDetails;
            return this;
        }

        public CandidateEvaluatedMessage build() {
            return new CandidateEvaluatedMessage(status, publicationUri, candidateDetails);
        }
    }
}