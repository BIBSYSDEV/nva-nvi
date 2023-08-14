package no.sikt.nva.nvi.common.service;

import java.net.URI;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.Candidate;

public class NviCandidateService {

    private NviCandidateRepository nviCandidateRepository;

    public NviCandidateService(NviCandidateRepository nviCandidateRepository) {
        this.nviCandidateRepository = nviCandidateRepository;
    }

    public Candidate getCandidateByPublicationId(URI publicationId){
        return nviCandidateRepository.findByPublicationId(publicationId.toString());
    }
}
