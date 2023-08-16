package no.sikt.nva.nvi.common;

import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.model.dao.Candidate;

public interface NviCandidateRepository {

    void save(Candidate candidate);

    Optional<Candidate> findByPublicationId(URI publicationId);
}
