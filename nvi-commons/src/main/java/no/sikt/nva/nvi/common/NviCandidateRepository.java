package no.sikt.nva.nvi.common;

import java.net.URI;
import java.util.Optional;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
import no.sikt.nva.nvi.common.model.business.Candidate;

public interface NviCandidateRepository {

    CandidateDb save(CandidateDb candidate);

    Optional<CandidateDb> findByPublicationId(URI publicationId);
}
