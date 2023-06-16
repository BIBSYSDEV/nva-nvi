package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CandidateResponse {

    private final URI publicationId;
    private final List<URI> approvalAffiliations;

    public CandidateResponse(URI publicationId, List<URI> approvalAffiliations) {
        this.publicationId = publicationId;
        this.approvalAffiliations = approvalAffiliations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public URI getPublicationId() {
        return publicationId;
    }

    public Collection<URI> getApprovalAffiliations() {
        return approvalAffiliations;
    }

    public static class Builder {

        private URI publicationId;
        private final Set<URI> approvalAffiliations = new HashSet<>();

        public Builder withPublicationId(URI publicationId) {
            this.publicationId = publicationId;
            return this;
        }

        public Builder withApprovalAffiliations(Collection<URI> approvalAffiliationUris) {
            this.approvalAffiliations.addAll(approvalAffiliationUris);
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(publicationId, new ArrayList<>(approvalAffiliations));
        }
    }
}
