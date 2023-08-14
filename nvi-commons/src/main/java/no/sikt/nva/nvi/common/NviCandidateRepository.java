package no.sikt.nva.nvi.common;

import no.sikt.nva.nvi.common.model.business.Candidate;

public interface NviCandidateRepository {

    void save(Candidate candidate);

    Candidate findByPublicationId(String publicationId);
}
