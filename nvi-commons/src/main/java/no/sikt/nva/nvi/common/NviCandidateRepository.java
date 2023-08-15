package no.sikt.nva.nvi.common;

import java.util.Optional;
import no.sikt.nva.nvi.common.model.business.Candidate;

public interface NviCandidateRepository {

    void save(Candidate candidate);

    Optional<Candidate> findByPublicationId(String publicationId);
}
