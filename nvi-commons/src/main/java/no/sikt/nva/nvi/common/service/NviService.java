package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.ApprovalStatus;
import no.sikt.nva.nvi.common.model.business.Candidate;
import no.sikt.nva.nvi.common.model.business.Status;

public class NviService {

    private final NviCandidateRepository nviCandidateRepository;

    public NviService(NviCandidateRepository nviCandidateRepository) {
        this.nviCandidateRepository = nviCandidateRepository;
    }

    public Optional<Candidate> getCandidateByPublicationId(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId.toString());
    }

    public boolean exists(URI publicationId) {
        return nviCandidateRepository.findByPublicationId(publicationId.toString()).isPresent();
    }

    public void createCandidate(URI publicationId, List<URI> institutionUri) {
        var pendingCandidate = new Candidate.Builder()
                                   .withPublicationId(publicationId)
                                   .withApprovalStatuses(generatePendingApprovalStatuses(institutionUri))
                                   .build();
        nviCandidateRepository.save(pendingCandidate);
    }

    private static List<ApprovalStatus> generatePendingApprovalStatuses(List<URI> institutionUri) {
        return institutionUri.stream().map(uri -> new ApprovalStatus.Builder()
                                                      .withStatus(Status.PENDING)
                                                      .withInstitutionId(uri)
                                                      .build()).toList();
    }
}
