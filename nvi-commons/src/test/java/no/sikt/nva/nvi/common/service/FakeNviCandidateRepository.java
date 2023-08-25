package no.sikt.nva.nvi.common.service;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.business.Candidate;

public class FakeNviCandidateRepository implements NviCandidateRepository {

    private final Map<URI, Candidate> candidateMap;

    public FakeNviCandidateRepository() {
        this.candidateMap = new ConcurrentHashMap<>();
    }

    @Override
    public Candidate save(Candidate candidate) {
        candidateMap.put(candidate.publicationId(), candidate);
        return candidate;
    }

    @Override
    public Optional<Candidate> findByPublicationId(URI publicationId) {
        return Optional.ofNullable(candidateMap.get(publicationId));
    }
}
