package no.sikt.nva.nvi.evaluator.model;

import java.net.URI;
import java.util.Collection;
import java.util.List;

public class CandidateResponse {

    private final URI publicationId;
    private final List<URI> approvalAffiliations;

    public CandidateResponse(URI publicationId, List<URI> approvalAffiliations) {
        this.publicationId = publicationId;
        this.approvalAffiliations = approvalAffiliations;
    }

    public URI getPublicationId() {
        return publicationId;
    }

    public Collection<URI> getApprovalAffiliations() {
        return approvalAffiliations;
    }
}
