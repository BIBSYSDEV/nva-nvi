package no.sikt.nva.nvi.common;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;

public interface NviCandidateRepository {

    CandidateWithIdentifier save(Candidate candidate);

    Optional<CandidateWithIdentifier> findById(UUID id);

    Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId);
}
