package no.sikt.nva.nvi.events.batch.message;

import java.util.UUID;

public record RefreshCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {}
