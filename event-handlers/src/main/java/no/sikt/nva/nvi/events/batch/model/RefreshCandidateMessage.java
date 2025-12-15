package no.sikt.nva.nvi.events.batch.model;

import java.util.UUID;

public record RefreshCandidateMessage(UUID candidateIdentifier) implements BatchJobMessage {}
