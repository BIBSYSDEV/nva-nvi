package handlers;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.db.dto.CandidateDb;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;

public class FakeNviCandidateRepository implements NviCandidateRepository {

    private final Map<URI, CandidateDb> candidateMap;

    public FakeNviCandidateRepository() {
        this.candidateMap = new ConcurrentHashMap<>();
    }

    @Override
    public CandidateDb save(CandidateDb candidate) {
        candidateMap.put(candidate.getPublicationId(), candidate);
        return candidate;
    }

    @Override
    public Optional<CandidateDb> findByPublicationId(URI publicationId) {
        return Optional.ofNullable(candidateMap.get(publicationId));
    }
}
