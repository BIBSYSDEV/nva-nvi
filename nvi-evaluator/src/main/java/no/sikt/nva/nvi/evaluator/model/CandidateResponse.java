package no.sikt.nva.nvi.evaluator.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.net.URI;
import java.util.List;
import no.sikt.nva.nvi.evaluator.calculator.NviCandidate.CandidateDetails;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonSerialize
public record CandidateResponse(CandidateStatus status,
                                URI publicationUri,
                                List<URI> approvalInstitutions,
                                CandidateDetails candidateDetails) {

    public static final class Builder {

        private CandidateStatus status;
        private URI publicationUri;
        private List<URI> approvalInstitutions;
        private CandidateDetails candidateDetails;

        public Builder() {
        }

        public Builder withStatus(CandidateStatus status) {
            this.status = status;
            return this;
        }

        public Builder withPublicationUri(URI publicationUri) {
            this.publicationUri = publicationUri;
            return this;
        }

        public Builder withApprovalInstitutions(List<URI> approvalInstitutions) {
            this.approvalInstitutions = approvalInstitutions;
            return this;
        }

        public Builder withCandidateDetails(CandidateDetails candidateDetails) {
            this.candidateDetails = candidateDetails;
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(status, publicationUri, approvalInstitutions, candidateDetails);
        }
    }
}