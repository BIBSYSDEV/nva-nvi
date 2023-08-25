package handlers;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import no.sikt.nva.nvi.common.NviCandidateRepository;
import no.sikt.nva.nvi.common.model.CandidateWithIdentifier;
import no.sikt.nva.nvi.common.model.business.Candidate;
import nva.commons.core.JacocoGenerated;

public class FakeNviCandidateRepository implements NviCandidateRepository {

    private final Map<UUID, Candidate> candidateMapId;
    private final Map<URI, CandidateWithIdentifier> candidateMapPublicationId;

    public FakeNviCandidateRepository() {

        this.candidateMapId = new ConcurrentHashMap<>();
        this.candidateMapPublicationId = new ConcurrentHashMap<>();
    }

    @Override
    public CandidateWithIdentifier save(Candidate candidate) {
        var uuid = UUID.randomUUID();
        var candidateWithIdentifier = new CandidateWithIdentifier(candidate, uuid);
        candidateMapId.put(uuid, candidate);
        candidateMapPublicationId.put(candidate.publicationId(), candidateWithIdentifier);
        return candidateWithIdentifier;
    }

    @Override
    public Optional<CandidateWithIdentifier> findById(UUID id) {
        return Optional.ofNullable(candidateMapId.get(id)).map(candidate -> new CandidateWithIdentifier(candidate, id));
    }

    @Override
    public Optional<CandidateWithIdentifier> findByPublicationId(URI publicationId) {
        return Optional.ofNullable(candidateMapPublicationId.get(publicationId));
    }
}
