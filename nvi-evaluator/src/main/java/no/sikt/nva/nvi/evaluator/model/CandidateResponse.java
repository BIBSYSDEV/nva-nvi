package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CandidateResponse {

    private final URI resourceUri;
    private final List<URI> approvalCandidates;

    public CandidateResponse(URI resourceUri, List<URI> approvalCandidates) {
        this.resourceUri = resourceUri;
        this.approvalCandidates = approvalCandidates;
    }

    public URI getResourceUri() {
        return resourceUri;
    }

    public Collection<URI> getApprovalCandidates() {
        return approvalCandidates;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URI resourceUri;
        private Set<URI> approvalCandidates = new HashSet<>();

        public Builder resourceUri(URI resourceUri) {
            this.resourceUri = resourceUri;
            return this;
        }

        public Builder approvalCandidates(Collection<URI> approvalCandidateUris) {
            this.approvalCandidates.addAll(approvalCandidateUris);
            return this;
        }

        public CandidateResponse build() {
            return new CandidateResponse(resourceUri, new ArrayList<>(approvalCandidates));
        }
    }
}
