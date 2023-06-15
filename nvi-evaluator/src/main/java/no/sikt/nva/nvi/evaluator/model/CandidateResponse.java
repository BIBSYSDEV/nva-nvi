package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CandidateResponse {

    private final URI resourceUri;
    private final List<URI> approvalAffiliations;

    public CandidateResponse(URI resourceUri, List<URI> approvalAffiliations) {
        this.resourceUri = resourceUri;
        this.approvalAffiliations = approvalAffiliations;
    }

    public URI getResourceUri() {
        return resourceUri;
    }

    public Collection<URI> getApprovalAffiliations() {
        return approvalAffiliations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URI resourceUri;
        private Set<URI> approvalAffiliations = new HashSet<>();

        public Builder resourceUri(URI resourceUri) {
            this.resourceUri = resourceUri;
            return this;
        }

        public Builder approvalAffiliations(Collection<URI> approvalAffiliationUris) {
            this.approvalAffiliations.addAll(approvalAffiliationUris);
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(resourceUri, new ArrayList<>(approvalAffiliations));
        }
    }
}
